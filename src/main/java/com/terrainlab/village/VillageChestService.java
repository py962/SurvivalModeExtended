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
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
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
 * double chest is used as a whole; any chest type or a barrel counts. Every query re-validates the
 * stored position, so a broken chest simply reads as "no chest" until {@link #ensureChest} adopts a
 * chest/barrel the player put next to the bell or places a new one (which starts empty: the
 * loot-table fill happens once per village).
 */
public final class VillageChestService {

    public static final ResourceKey<LootTable> FOOD_CHEST_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "chests/village_food_chest"));

    /** Chest search/placement rings around the bell (Chebyshev distance). */
    private static final int MAX_RING = 3;
    /** Height range searched relative to the bell: bells often hang above or sit on pedestals. */
    private static final int MIN_DY = -3;
    private static final int MAX_DY = 1;

    private VillageChestService() {
    }

    // ---------------------------------------------------------------- queries

    /** The village's chest position, if one is recorded, loaded and still a chest or barrel. */
    public static Optional<BlockPos> findChest(ServerLevel level, BlockPos bellPos) {
        Optional<VillageRecord> record = VillageSavedData.get(level).find(new VillageId(level.dimension(), bellPos));
        if (record.isEmpty()) {
            return Optional.empty();
        }
        return record.get().getChestPos().filter(pos -> isChestAt(level, pos));
    }

    /** The village chest's inventory (whole double chest if extended; a barrel's own), or empty. */
    public static Optional<Container> getContainer(ServerLevel level, BlockPos bellPos) {
        return findChest(level, bellPos).map(pos -> {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock chest) {
                return ChestBlock.getContainer(chest, state, level, pos, true);
            }
            return level.getBlockEntity(pos) instanceof Container container ? container : null;
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
     * True when the village at {@code bellPos} (bell loaded) has no usable chest yet: no record, no
     * recorded position, or the recorded (loaded) block is no longer a chest or barrel. Cheap; used to
     * run {@link #ensureChest} promptly for newly discovered villages and broken/replaced chests.
     */
    public static boolean needsChest(ServerLevel level, BlockPos bellPos) {
        if (!level.isLoaded(bellPos)) {
            return false;
        }
        Optional<VillageRecord> record = VillageSavedData.get(level).find(new VillageId(level.dimension(), bellPos));
        if (record.isEmpty() || record.get().getChestPos().isEmpty()) {
            return true;
        }
        BlockPos pos = record.get().getChestPos().get();
        return level.isLoaded(pos) && !isChestAt(level, pos);
    }

    /**
     * Makes sure the village at {@code bellPos} has a chest. A recorded position is kept as long as it
     * holds any chest or barrel (so a player may swap the block for another chest type). Otherwise an
     * existing chest or barrel within {@link #MAX_RING} blocks of the bell is adopted (nearest first),
     * and only if there is none a new chest is placed (see {@link #findFreeSpot} and
     * {@link #findGroundCoverSpot}). Only the very first chest a village gets is filled from
     * {@link #FOOD_CHEST_LOOT}; adopted and replacement chests are never stocked. Does nothing if the
     * bell or the recorded chest is in an unloaded chunk.
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

        // A chest or barrel the player put next to the bell becomes the village chest.
        Optional<BlockPos> adopted = findExistingChest(level, bellPos);
        if (adopted.isPresent()) {
            record.setChestPos(adopted.get());
            record.setChestStocked(true); // adopted chests are never stocked, nor are later replacements
            saved.setDirty();
            return adopted;
        }

        Optional<Placement> spot = findFreeSpot(level, bellPos);
        if (spot.isEmpty()) {
            spot = findGroundCoverSpot(level, bellPos);
        }
        if (spot.isEmpty()) {
            return Optional.empty();
        }
        BlockPos pos = spot.get().pos();
        BlockState chest = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, spot.get().facing())
                .setValue(ChestBlock.TYPE, ChestType.SINGLE);
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

    /** A chest spot and the facing that keeps it a single chest. */
    private record Placement(BlockPos pos, Direction facing) {
    }

    /**
     * Candidate positions around the bell: Chebyshev rings 1..{@link #MAX_RING}, each from
     * {@link #MIN_DY} to {@link #MAX_DY} blocks of height. Within a ring, nearest to the bell first;
     * ties prefer bell height or below over above, then the smaller height difference.
     */
    private static List<List<BlockPos>> candidateRings(BlockPos bellPos) {
        List<List<BlockPos>> rings = new ArrayList<>();
        for (int ring = 1; ring <= MAX_RING; ring++) {
            List<BlockPos> candidates = new ArrayList<>();
            for (int dy = MIN_DY; dy <= MAX_DY; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                            candidates.add(bellPos.offset(dx, dy, dz));
                        }
                    }
                }
            }
            candidates.sort(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(bellPos))
                    .thenComparingInt(p -> p.getY() > bellPos.getY() ? 1 : 0)
                    .thenComparingInt(p -> Math.abs(p.getY() - bellPos.getY())));
            rings.add(candidates);
        }
        return rings;
    }

    /** The nearest loaded chest or barrel within the candidate rings, if any. */
    private static Optional<BlockPos> findExistingChest(ServerLevel level, BlockPos bellPos) {
        for (List<BlockPos> ring : candidateRings(bellPos)) {
            for (BlockPos pos : ring) {
                if (isChestAt(level, pos)) {
                    return Optional.of(pos.immutable());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * A free spot next to the bell (rings and order per {@link #candidateRings}). The spot must be
     * loaded, air or a replaceable non-fluid block (grass, flowers, snow layer), sit on a block with a
     * sturdy top, have air or a replaceable block above for the lid, and allow a facing in which the
     * new chest cannot merge with a neighbouring chest. Never replaces solid or player-placed blocks.
     */
    private static Optional<Placement> findFreeSpot(ServerLevel level, BlockPos bellPos) {
        for (List<BlockPos> ring : candidateRings(bellPos)) {
            for (BlockPos pos : ring) {
                if (!isFreeSpot(level, pos)) {
                    continue;
                }
                Optional<Direction> facing = singleChestFacing(level, pos, bellPos);
                if (facing.isPresent()) {
                    return Optional.of(new Placement(pos.immutable(), facing.get()));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Last resort: in ring 1, the topmost natural ground-cover block ({@link #isNaturalGroundCover})
     * of each column within {@link #MIN_DY}..0 of the bell, with air above; the chest replaces it and
     * sits flush with the ground. Nearest to the bell first.
     */
    private static Optional<Placement> findGroundCoverSpot(ServerLevel level, BlockPos bellPos) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 0; dy >= MIN_DY; dy--) {
                    BlockPos pos = bellPos.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        break;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (isNaturalGroundCover(state) && level.getBlockState(pos.above()).isAir()) {
                        candidates.add(pos.immutable());
                    }
                    break; // the first non-air block from the top is the ground of this column
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(bellPos)));
        for (BlockPos pos : candidates) {
            Optional<Direction> facing = singleChestFacing(level, pos, bellPos);
            if (facing.isPresent()) {
                return Optional.of(new Placement(pos, facing.get()));
            }
        }
        return Optional.empty();
    }

    private static boolean isNaturalGroundCover(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.PODZOL) || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean isFreeSpot(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || !isAirOrReplaceable(level.getBlockState(pos))) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        return isAirOrReplaceable(level.getBlockState(pos.above())); // room for the lid
    }

    private static boolean isAirOrReplaceable(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    /**
     * A facing for a new single chest at {@code pos} in which it cannot form a double chest: neither
     * side neighbour (left/right of the facing) may be a chest of the same block. The preferred facing
     * points away from the bell; empty if every facing would sit beside a chest.
     */
    private static Optional<Direction> singleChestFacing(ServerLevel level, BlockPos pos, BlockPos bellPos) {
        Direction preferred = Direction.getApproximateNearest(
                (float) (pos.getX() - bellPos.getX()), 0.0F, (float) (pos.getZ() - bellPos.getZ()));
        if (preferred.getAxis() == Direction.Axis.Y) {
            preferred = Direction.NORTH;
        }
        ChestBlock chest = (ChestBlock) Blocks.CHEST;
        Direction facing = preferred;
        for (int i = 0; i < 4; i++) {
            if (!chest.chestCanConnectTo(level.getBlockState(pos.relative(facing.getClockWise())))
                    && !chest.chestCanConnectTo(level.getBlockState(pos.relative(facing.getCounterClockWise())))) {
                return Optional.of(facing);
            }
            facing = facing.getClockWise();
        }
        return Optional.empty();
    }

    /** A loaded chest (any {@link ChestBlock}: wooden, trapped, copper, modded) or barrel. */
    private static boolean isChestAt(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        Block block = level.getBlockState(pos).getBlock();
        return block instanceof ChestBlock || block instanceof BarrelBlock;
    }
}
