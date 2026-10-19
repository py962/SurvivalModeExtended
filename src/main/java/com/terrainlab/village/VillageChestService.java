package com.terrainlab.village;

import com.terrainlab.TerrainLab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The village food chest: one chest next to each village bell, its position kept in
 * {@link VillageRecord}. Villagers eat from it ({@link VillagerChestMealService}); worker
 * professions can deposit into it.
 * <p>
 * All calls are server-side and only touch loaded chunks. A chest the player has extended into a
 * double chest is used as a whole. Every query re-validates the stored position, so a broken chest
 * simply reads as "no chest" until {@link #ensureChest} places a new one (which starts empty: the
 * loot-table fill happens once per village).
 */
public final class VillageChestService {

    public static final ResourceKey<LootTable> FOOD_CHEST_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "chests/village_food_chest"));

    private static final int MAX_RING = 2;

    private VillageChestService() {
    }

    // ---------------------------------------------------------------- queries

    /** The village's chest position, if one is recorded, loaded and still a chest. */
    public static Optional<BlockPos> findChest(ServerLevel level, BlockPos bellPos) {
        Optional<VillageRecord> record = VillageSavedData.get(level).find(new VillageId(level.dimension(), bellPos));
        if (record.isEmpty()) {
            return Optional.empty();
        }
        return record.get().getChestPos().filter(pos -> isChestAt(level, pos));
    }

    /** The village chest's inventory (whole double chest if extended), or empty. */
    public static Optional<Container> getContainer(ServerLevel level, BlockPos bellPos) {
        return findChest(level, bellPos).map(pos -> {
            BlockState state = level.getBlockState(pos);
            return ChestBlock.getContainer((ChestBlock) state.getBlock(), state, level, pos, true);
        });
    }

    /** Total count of items in the chest matching {@code filter}; 0 with no chest. */
    public static int count(ServerLevel level, BlockPos bellPos, Predicate<ItemStack> filter) {
        Optional<Container> container = getContainer(level, bellPos);
        if (container.isEmpty()) {
            return 0;
        }
        int total = 0;
        Container c = container.get();
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * The highest-scoring item in the chest (score > 0 only; ties go to the first slot), returned as a
     * copy of one item without removing it. Pass the result to {@link #take} with
     * {@code s -> ItemStack.isSameItemSameComponents(s, best)} to remove it.
     */
    public static Optional<ItemStack> findBest(ServerLevel level, BlockPos bellPos, ToIntFunction<ItemStack> score) {
        Optional<Container> container = getContainer(level, bellPos);
        if (container.isEmpty()) {
            return Optional.empty();
        }
        Container c = container.get();
        ItemStack best = ItemStack.EMPTY;
        int bestScore = 0;
        for (int slot = 0; slot < c.getContainerSize(); slot++) {
            ItemStack stack = c.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int s = score.applyAsInt(stack);
            if (s > bestScore) {
                bestScore = s;
                best = stack;
            }
        }
        return best.isEmpty() ? Optional.empty() : Optional.of(best.copyWithCount(1));
    }

    // ---------------------------------------------------------------- mutation

    /**
     * Puts {@code stack} into the village chest, topping up matching stacks first, then empty slots.
     * The argument is not modified.
     *
     * @return what did not fit (the whole stack when there is no chest); {@link ItemStack#EMPTY} if all fit
     */
    public static ItemStack deposit(ServerLevel level, BlockPos bellPos, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Optional<Container> container = getContainer(level, bellPos);
        if (container.isEmpty()) {
            return stack.copy();
        }
        Container c = container.get();
        ItemStack remaining = stack.copy();
        boolean changed = false;

        // Merge into existing stacks.
        for (int slot = 0; slot < c.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = c.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)
                    || !c.canPlaceItem(slot, remaining)) {
                continue;
            }
            int room = c.getMaxStackSize(existing) - existing.getCount();
            if (room > 0) {
                int moved = Math.min(room, remaining.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
                changed = true;
            }
        }
        // Then empty slots.
        for (int slot = 0; slot < c.getContainerSize() && !remaining.isEmpty(); slot++) {
            if (!c.getItem(slot).isEmpty() || !c.canPlaceItem(slot, remaining)) {
                continue;
            }
            int moved = Math.min(c.getMaxStackSize(remaining), remaining.getCount());
            c.setItem(slot, remaining.split(moved));
            changed = true;
        }

        if (changed) {
            c.setChanged();
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    /**
     * Removes up to {@code count} items from the chest. The first slot matching {@code filter} decides
     * the item type; further items are only taken from stacks of that same item and components, so the
     * result is always a single stack.
     *
     * @return the removed items, or {@link ItemStack#EMPTY} if nothing matched or there is no chest
     */
    public static ItemStack take(ServerLevel level, BlockPos bellPos, Predicate<ItemStack> filter, int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        Optional<Container> container = getContainer(level, bellPos);
        if (container.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Container c = container.get();
        ItemStack result = ItemStack.EMPTY;

        for (int slot = 0; slot < c.getContainerSize() && result.getCount() < count; slot++) {
            ItemStack stack = c.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (result.isEmpty()) {
                if (!filter.test(stack)) {
                    continue;
                }
                result = c.removeItem(slot, Math.min(count, stack.getCount()));
            } else if (ItemStack.isSameItemSameComponents(stack, result)) {
                ItemStack more = c.removeItem(slot, Math.min(count - result.getCount(), stack.getCount()));
                result.grow(more.getCount());
            }
        }

        if (!result.isEmpty()) {
            c.setChanged();
        }
        return result;
    }

    // ---------------------------------------------------------------- placement

    /**
     * Makes sure the village at {@code bellPos} has a chest: if none is recorded, or the recorded block
     * is no longer a chest, a new one is placed on a free spot next to the bell (see
     * {@link #findFreeSpot}). The first chest a village ever gets is filled from
     * {@link #FOOD_CHEST_LOOT}; later replacements start empty. Does nothing if the bell or the recorded
     * chest is in an unloaded chunk, or no free spot exists.
     *
     * @return the chest position, if the village now has one
     */
    public static Optional<BlockPos> ensureChest(ServerLevel level, BlockPos bellPos) {
        if (!level.isLoaded(bellPos) || !level.getBlockState(bellPos).is(Blocks.BELL)) {
            return Optional.empty();
        }
        VillageSavedData saved = VillageSavedData.get(level);
        VillageRecord record = saved.getOrCreate(new VillageId(level.dimension(), bellPos));

        Optional<BlockPos> recorded = record.getChestPos();
        if (recorded.isPresent()) {
            BlockPos pos = recorded.get();
            if (!level.isLoaded(pos)) {
                return Optional.empty();
            }
            if (isChestAt(level, pos)) {
                return recorded;
            }
        }

        Optional<BlockPos> spot = findFreeSpot(level, bellPos);
        if (spot.isEmpty()) {
            return Optional.empty();
        }
        BlockPos pos = spot.get();

        Direction facing = Direction.getApproximateNearest(
                (float) (pos.getX() - bellPos.getX()), 0.0F, (float) (pos.getZ() - bellPos.getZ()));
        if (facing.getAxis() == Direction.Axis.Y) {
            facing = Direction.NORTH;
        }
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        if (!level.setBlock(pos, chest, Block.UPDATE_ALL)) {
            return Optional.empty();
        }

        if (!record.isChestStocked()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RandomizableContainerBlockEntity container) {
                container.setLootTable(FOOD_CHEST_LOOT, level.getRandom().nextLong());
                container.unpackLootTable(null);
            }
            record.setChestStocked(true);
        }
        record.setChestPos(pos);
        saved.setDirty();
        return Optional.of(pos);
    }

    /**
     * A free spot next to the bell: rings of radius 1 then 2 around it, each at bell height and then
     * one block lower (ground beside a bell on a pedestal), nearest first. The spot must be loaded, air
     * or a replaceable non-fluid block (grass, snow layer), sit on a sturdy block, have room above for
     * the lid, and not touch another chest. Never replaces solid or player-placed blocks.
     */
    private static Optional<BlockPos> findFreeSpot(ServerLevel level, BlockPos bellPos) {
        for (int ring = 1; ring <= MAX_RING; ring++) {
            for (int dy : new int[]{0, -1}) {
                List<BlockPos> candidates = new ArrayList<>();
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                            candidates.add(bellPos.offset(dx, dy, dz));
                        }
                    }
                }
                candidates.sort(Comparator.comparingDouble(p -> p.distSqr(bellPos)));
                for (BlockPos pos : candidates) {
                    if (isFreeSpot(level, pos)) {
                        return Optional.of(pos);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isFreeSpot(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        boolean free = state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
        if (!free) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        BlockPos above = pos.above();
        if (level.getBlockState(above).isRedstoneConductor(level, above)) {
            return false; // the lid could not open
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(dir)).getBlock() instanceof ChestBlock) {
                return false; // would sit against another chest
            }
        }
        return true;
    }

    private static boolean isChestAt(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof ChestBlock;
    }
}
