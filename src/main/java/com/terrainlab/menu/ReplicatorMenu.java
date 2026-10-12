package com.terrainlab.menu;

import com.terrainlab.blockentity.ReplicatorBlockEntity;
import com.terrainlab.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Menu for the Replicator: input, Enderwood fuel and output slots plus synced burn/progress/redstone data. */
public class ReplicatorMenu extends AbstractContainerMenu {

    private final ReplicatorBlockEntity blockEntity;
    private final ContainerData data;

    public ReplicatorMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, getBlockEntity(inventory, buf), new SimpleContainerData(ReplicatorBlockEntity.DATA_COUNT));
    }

    private static ReplicatorBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        return (ReplicatorBlockEntity) be;
    }

    public ReplicatorMenu(int id, Inventory inventory,
                          ReplicatorBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.REPLICATOR_MENU.get(), id);
        this.blockEntity = blockEntity;
        this.data = data;

        addSlot(new Slot(blockEntity, ReplicatorBlockEntity.SLOT_INPUT, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ReplicatorBlockEntity.isValidInputPublic(stack);
            }

            @Override
            public int getMaxStackSize() {
                return ReplicatorBlockEntity.MAX_INPUT; // 32 in -> 64 out, one stack
            }
        });

        addSlot(new Slot(blockEntity, ReplicatorBlockEntity.SLOT_FUEL, 56, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return ReplicatorBlockEntity.isEnderwoodPublic(stack);
            }
        });

        addSlot(new Slot(blockEntity, ReplicatorBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "replicator");
                }
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    public ReplicatorBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** A batch is running (started by a pulse). */
    public boolean isRunning() {
        return data.get(4) != 0;
    }

    /** Strongest incoming redstone signal, 0-15. */
    public int getInputPower() {
        return data.get(5);
    }

    /** Batch progress 0..1. */
    public float getProgress() {
        int max = data.get(3);
        return max != 0 ? data.get(2) / (float) max : 0f;
    }

    /** Burn remaining 0..1. */
    public float getFuel() {
        int duration = data.get(1);
        return duration != 0 ? data.get(0) / (float) duration : 0f;
    }

    /** Same value the block gives a comparator (0-15). */
    public int getComparatorSignal() {
        if (isRunning()) {
            return Math.max(1, (int) (getProgress() * 15));
        }
        ItemStack out = getSlot(2).getItem();
        return out.isEmpty() ? 0 : 1 + out.getCount() * 14 / out.getMaxStackSize();
    }

    public boolean isBurning() {
        return data.get(0) > 0;
    }

    public int getScaledProgress() {
        int progress = data.get(2);
        int maxProgress = data.get(3);
        return maxProgress != 0 ? progress * 24 / maxProgress : 0;
    }

    public int getScaledFuel() {
        int burnTime = data.get(0);
        int burnDuration = data.get(1);
        return burnDuration != 0 ? burnTime * 13 / burnDuration : 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }
}
