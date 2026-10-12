package com.terrainlab.blockentity;

import com.terrainlab.menu.ReplicatorMenu;
import com.terrainlab.registry.ModBlockEntities;
import com.terrainlab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import com.terrainlab.TerrainLab;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Replicator bench: doubles up to 32 wood or stone blocks per batch, burning
 * enderwood as fuel. Like a crafter, it only starts a batch on a redstone rising
 * edge or the GUI button; a pulse that cannot start plays the dispenser fail click.
 * <p>
 * Hoppers: top inserts input, sides insert fuel, bottom extracts output.
 * Comparator: batch progress (1-15) while running, otherwise output fullness.
 */
public class ReplicatorBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;
    private static final int CONTAINER_SIZE = 3;
    private static final int MAX_PROGRESS = 100; // ticks of burning per batch
    /** Largest input batch; 32 in gives one full stack out. */
    public static final int MAX_INPUT = 32;
    private static final int PULSE_COOLDOWN = 4; // ticks

    private static final int[] SLOTS_TOP = {SLOT_INPUT};
    private static final int[] SLOTS_SIDES = {SLOT_FUEL};
    private static final int[] SLOTS_BOTTOM = {SLOT_OUTPUT};

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    private int burnTime = 0;
    private int burnDuration = 0;
    private int progress = 0;
    /** A pulse-started batch is in progress. */
    private boolean running = false;
    /** Last seen redstone input (for rising-edge detection). */
    private boolean powered = false;
    /** Strongest incoming redstone signal, 0-15, shown on the GUI's input wire. */
    private int inputPower = 0;
    private int cooldown = 0;
    private int lastComparatorSignal = 0;

    public static final int DATA_COUNT = 6;

    protected final ContainerData dataAccess = new ContainerData() {
        public int get(int index) {
            return switch (index) {
                case 0 -> ReplicatorBlockEntity.this.burnTime;
                case 1 -> ReplicatorBlockEntity.this.burnDuration;
                case 2 -> ReplicatorBlockEntity.this.progress;
                case 3 -> MAX_PROGRESS;
                case 4 -> ReplicatorBlockEntity.this.running ? 1 : 0;
                case 5 -> ReplicatorBlockEntity.this.inputPower;
                default -> 0;
            };
        }
        public void set(int index, int value) {
            switch (index) {
                case 0 -> ReplicatorBlockEntity.this.burnTime = value;
                case 1 -> ReplicatorBlockEntity.this.burnDuration = value;
                case 2 -> ReplicatorBlockEntity.this.progress = value;
                case 4 -> ReplicatorBlockEntity.this.running = value != 0;
                case 5 -> ReplicatorBlockEntity.this.inputPower = value;
            }
        }
        public int getCount() { return DATA_COUNT; }
    };

    public ReplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REPLICATOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos,
                            BlockState state, ReplicatorBlockEntity be) {
        if (level.isClientSide()) return;

        if (be.cooldown > 0) {
            be.cooldown--;
        }

        if (be.running) {
            ItemStack input = be.items.get(SLOT_INPUT);
            if (input.isEmpty() || !isValidInput(input)) {
                // Input was taken out mid-batch - abandon it.
                be.running = false;
                be.progress = 0;
                be.setChanged();
            } else {
                // Fuel only burns while a batch runs; an empty fuel slot pauses the batch.
                if (!be.isBurning()) {
                    ItemStack fuel = be.items.get(SLOT_FUEL);
                    if (!fuel.isEmpty() && isEnderwood(fuel)) {
                        be.burnTime = 200;
                        be.burnDuration = 200;
                        fuel.shrink(1);
                        be.setChanged();
                    }
                }
                if (be.isBurning()) {
                    be.burnTime--;
                    be.progress++;
                    if (be.progress >= MAX_PROGRESS) {
                        be.finishBatch(level, pos);
                    }
                }
            }
        }

        int signal = be.comparatorSignal();
        if (signal != be.lastComparatorSignal) {
            be.lastComparatorSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private void finishBatch(Level level, BlockPos pos) {
        ItemStack input = items.get(SLOT_INPUT);
        ItemStack output = items.get(SLOT_OUTPUT);
        ItemStack result = input.copyWithCount(Math.min(input.getCount() * 2, input.getMaxStackSize()));
        // The input may have changed since the batch started; abandon it if the result no longer fits.
        boolean fits = output.isEmpty()
                || (ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize());
        if (!fits) {
            progress = 0;
            running = false;
            setChanged();
            level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, pos, 0);
            return;
        }
        if (output.isEmpty()) {
            items.set(SLOT_OUTPUT, result);
        } else {
            output.grow(result.getCount());
        }
        input.shrink(input.getCount());
        progress = 0;
        running = false;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.levelEvent(LevelEvent.SOUND_CRAFTER_CRAFT, pos, 0);
            serverLevel.sendParticles(DustParticleOptions.REDSTONE,
                    pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 12, 0.3, 0.05, 0.3, 0.0);
        }
    }

    /**
     * Handles one pulse (redstone rising edge or GUI button): starts a batch if
     * possible, else plays the fail click. Ignored while running or cooling down.
     */
    public void trigger() {
        if (level == null || level.isClientSide() || running || cooldown > 0) {
            return;
        }
        cooldown = PULSE_COOLDOWN;
        if (!canStartBatch()) {
            level.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL, worldPosition, 0);
            return;
        }
        running = true;
        progress = 0;
        setChanged();
    }

    private boolean canStartBatch() {
        ItemStack input = items.get(SLOT_INPUT);
        if (input.isEmpty() || !isValidInput(input)) {
            return false;
        }
        ItemStack fuel = items.get(SLOT_FUEL);
        if (!isBurning() && (fuel.isEmpty() || !isEnderwood(fuel))) {
            return false;
        }
        int outCount = Math.min(input.getCount() * 2, input.getMaxStackSize());
        ItemStack output = items.get(SLOT_OUTPUT);
        return output.isEmpty()
                || (ItemStack.isSameItemSameComponents(output, input)
                && output.getCount() + outCount <= output.getMaxStackSize());
    }

    /** Called from ReplicatorBlock#neighborChanged. */
    public void onRedstone(boolean nowPowered, int strength) {
        inputPower = strength;
        if (nowPowered && !powered) {
            trigger();
        }
        powered = nowPowered;
    }

    /** Comparator: batch progress (1-15) while running, else output fullness (0-15). */
    public int comparatorSignal() {
        if (running) {
            return Math.max(1, progress * 15 / MAX_PROGRESS);
        }
        ItemStack output = items.get(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return 0;
        }
        return 1 + output.getCount() * 14 / output.getMaxStackSize();
    }

    // Hopper faces (WorldlyContainer)
    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP -> SLOTS_TOP;
            case DOWN -> SLOTS_BOTTOM;
            default -> SLOTS_SIDES;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @javax.annotation.Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT && side == Direction.DOWN;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_INPUT -> isValidInput(stack) && items.get(SLOT_INPUT).getCount() < MAX_INPUT;
            case SLOT_FUEL -> isEnderwood(stack);
            default -> false;
        };
    }

    public boolean isBurning() { return burnTime > 0; }

    static boolean isValidInput(ItemStack stack) {
        return isStone(stack) || isWood(stack);
    }

    public static boolean isEnderwoodPublic(ItemStack stack) {
        return isEnderwood(stack);
    }

    public static boolean isValidInputPublic(ItemStack stack) {
        boolean result = isValidInput(stack);
        return result;
    }

    private static boolean isWood(ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.LOGS);
    }

    private static boolean isStone(ItemStack stack) {
        return stack.is(Items.COBBLESTONE)
                || stack.is(Items.STONE)
                || stack.is(Items.GRANITE)
                || stack.is(Items.POLISHED_GRANITE)
                || stack.is(Items.DIORITE)
                || stack.is(Items.POLISHED_DIORITE)
                || stack.is(Items.ANDESITE)
                || stack.is(Items.POLISHED_ANDESITE)
                || stack.is(Items.DEEPSLATE)
                || stack.is(Items.COBBLED_DEEPSLATE)
                || stack.is(Items.POLISHED_DEEPSLATE)
                || stack.is(Items.TUFF)
                || stack.is(Items.POLISHED_TUFF)
                || stack.is(Items.SANDSTONE)
                || stack.is(Items.CHISELED_SANDSTONE)
                || stack.is(Items.SMOOTH_SANDSTONE)
                || stack.is(Items.RED_SANDSTONE)
                || stack.is(Items.CHISELED_RED_SANDSTONE)
                || stack.is(Items.SMOOTH_RED_SANDSTONE)
                || stack.is(Items.BLACKSTONE)
                || stack.is(Items.POLISHED_BLACKSTONE)
                || stack.is(Items.BASALT)
                || stack.is(Items.SMOOTH_BASALT)
                || stack.is(Items.POLISHED_BASALT)
                || stack.is(Items.CALCITE)
                || stack.is(Items.DRIPSTONE_BLOCK)
                || stack.is(Items.NETHERRACK)
                || stack.is(Items.END_STONE)
                || stack.is(Items.SAND)
                || stack.is(Items.RED_SAND)
                || stack.is(Items.GRAVEL)
                || stack.is(Items.DIRT)
                || stack.is(Items.COARSE_DIRT)
                || stack.is(Items.ROOTED_DIRT)
                || stack.is(Items.CLAY)
                || stack.is(Items.MUD)
                || stack.is(Items.PACKED_MUD)
                || stack.is(Items.ICE)
                || stack.is(Items.PACKED_ICE)
                || stack.is(Items.BLUE_ICE)
                || stack.is(Items.SNOW_BLOCK)
                || stack.is(Items.CRIMSON_NYLIUM)
                || stack.is(Items.WARPED_NYLIUM);
    }

    private static boolean isEnderwood(ItemStack stack) {
        return stack.is(ModBlocks.ENDERWOOD.get().asItem());
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.terrainlab.replicator");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ReplicatorMenu(id, inventory, this, dataAccess);
    }

    @Override
    public int getContainerSize() { return CONTAINER_SIZE; }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getCount() <= amount) {
            items.set(slot, ItemStack.EMPTY);
            setChanged();
            return stack;
        }
        ItemStack split = stack.split(amount);
        setChanged();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.replaceAll(s -> ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            items.set(i, input.read("Item" + i, ItemStack.OPTIONAL_CODEC)
                    .orElse(ItemStack.EMPTY));
        }
        burnTime = input.getIntOr("BurnTime", 0);
        burnDuration = input.getIntOr("BurnDuration", 0);
        progress = input.getIntOr("Progress", 0);
        running = input.getBooleanOr("Running", false);
        powered = input.getBooleanOr("Powered", false);
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            if (!items.get(i).isEmpty()) {
                output.store("Item" + i, ItemStack.OPTIONAL_CODEC, items.get(i));
            }
        }
        output.putInt("BurnTime", burnTime);
        output.putInt("BurnDuration", burnDuration);
        output.putInt("Progress", progress);
        output.putBoolean("Running", running);
        output.putBoolean("Powered", powered);
    }
}
