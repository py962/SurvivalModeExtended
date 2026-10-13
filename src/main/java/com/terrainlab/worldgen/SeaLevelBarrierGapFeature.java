package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the aquifer barrier walls recorded by AquiferBarrierRecorder for this
 * chunk into dams with occasional rapids between the two pools they separate.
 * <p>
 * The recorded span only identifies which columns are wall; heights are measured
 * from the built world, since the barrier band is centred between the two fluid
 * levels and jittered by noise. H is the upper pool's surface: the highest water
 * cardinally adjacent to a wall column.
 * <ol>
 *   <li>Measure (pass A): find H and which sides hold water at H for every column,
 *   before anything is written, so later passes use a snapshot and an opened
 *   column never becomes "water behind" its neighbour.</li>
 *   <li>Lip (pass B): where the rapids lattice allows, open a straight run of at
 *   most MAX_CARVE_THICKNESS at y == H with water behind and air or water beyond,
 *   clear all solid above it, and extend the sill at H - 1 upstream. Runs whose
 *   rock above reaches BARRIER_MAX_Y are left solid.</li>
 *   <li>Dam (pass C): seal every column from H - 1 down to the river bed with the
 *   column's own wall material.</li>
 * </ol>
 * Placed at the lakes step in every Overworld biome via the sea_level_barrier_gap
 * biome modifier.
 */
public final class SeaLevelBarrierGapFeature implements Feature {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/SeaLevelBarrierGap]";

    // Runs thicker than this stay solid as rocks between the rapids; runs are never partially carved.
    private static final int MAX_CARVE_THICKNESS = 2;

    // Rapids lattice: cell size (opening width, blocks) and how many cells in eight are open.
    private static final int RAPIDS_CELL_SIZE = 3;
    private static final int RAPIDS_OPEN_IN_EIGHT = 4;

    // Blocks the sill at H - 1 is extended upstream, forming a shallow rock shelf ahead of the lip.
    private static final int LIP_UPSTREAM_REACH = 3;

    // Window around the barrier span's maxY searched for the adjacent pool surface. Columns with
    // no water in it are not river junctions and are skipped entirely.
    private static final int WATER_PROBE_ABOVE = 3;
    private static final int WATER_PROBE_BELOW = 6;

    // Per-chunk summary at INFO, for debugging only.
    private static final boolean CHUNK_SUMMARY_LOGGING = false;

    private static final int[][] NEIGHBOR_OFFSETS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    public static final MapCodec<SeaLevelBarrierGapFeature> CODEC = MapCodec.unit(SeaLevelBarrierGapFeature::new);

    public SeaLevelBarrierGapFeature() {
    }

    @Override
    public MapCodec<SeaLevelBarrierGapFeature> codec() {
        return CODEC;
    }

    /** One recorded wall column, after pass A has measured it. */
    private record WallColumn(int x, int z, int minY, int maxY, int waterLine, boolean[] waterBehind) {
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ChunkPos chunkPos = ChunkPos.containing(origin);

        Map<Integer, int[]> recorded = AquiferBarrierRecorder.takeChunk(level.getLevel().dimension(), chunkPos.x(), chunkPos.z());
        if (recorded.isEmpty()) {
            return false;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Pass A: measure only. Sorted for reproducible processing order.
        List<WallColumn> columns = new ArrayList<>();
        int droppedNoPool = 0;

        for (Map.Entry<Integer, int[]> entry : recorded.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            int worldX = chunkPos.getMinBlockX() + ((entry.getKey() >> 4) & 15);
            int worldZ = chunkPos.getMinBlockZ() + (entry.getKey() & 15);
            int minY = entry.getValue()[AquiferBarrierRecorder.MIN_Y];
            int maxY = entry.getValue()[AquiferBarrierRecorder.MAX_Y];

            WallColumn column = measure(level, pos, worldX, worldZ, minY, maxY);
            if (column == null) {
                droppedNoPool++;
                continue;
            }
            columns.add(column);
        }

        if (columns.isEmpty()) {
            if (CHUNK_SUMMARY_LOGGING) {
                // LOGGER.info("{} chunk {} - {} recorded barrier column(s), all dropped: no adjacent pool",
                        // LOG_PREFIX, chunkPos, recorded.size());
            }
            return false;
        }

        // Pass B: open lips at y == H and clear above them.
        int openedRuns = 0;
        int openedBlocks = 0;
        int removedAbove = 0;
        int apronBlocks = 0;
        int refusedTooTall = 0;

        for (WallColumn column : columns) {
            if (!isRapidsOpening(column.x(), column.z())) {
                continue; // rock between the rapids
            }
            int[] result = tryOpenLip(level, pos, column);
            if (result == null) {
                continue;
            }
            if (result[0] < 0) {
                refusedTooTall++;
                continue;
            }
            openedRuns++;
            openedBlocks += result[0];
            removedAbove += result[1];
            apronBlocks += result[2];
        }

        // Pass C: dam.
        int sealed = 0;
        int skippedNoMaterial = 0;

        for (WallColumn column : columns) {
            BlockState fillMaterial = wallMaterial(level, pos, column);
            if (fillMaterial == null) {
                skippedNoMaterial++;
                continue;
            }
            sealed += sealDown(level, pos, column, fillMaterial);
        }

        if (CHUNK_SUMMARY_LOGGING) {
            // LOGGER.info("{} chunk {} - {} recorded column(s), {} dropped (no adjacent pool); "
                        // + "{} run(s) opened at the water line ({} block(s)) with {} block(s) removed "
                        // + "above them and {} block(s) of sill apron built upstream, {} refused "
                        // + "(solid above the lip all the way to y={}); {} block(s) sealed below the "
                        // + "water line ({} column(s) had no solid material in the observed span)",
                // LOG_PREFIX, chunkPos, recorded.size(), droppedNoPool,
                // openedRuns, openedBlocks, removedAbove, apronBlocks, refusedTooTall,
                    // SeaLevelBandingService.BARRIER_MAX_Y, sealed, skippedNoMaterial);
        }

        return openedBlocks > 0 || sealed > 0;
    }

    /**
     * Pass A: finds H as the highest water block cardinally adjacent to the column near the top of
     * the barrier span, and snapshots which sides hold water at H.
     *
     * @return the measured column, or null if no adjacent pool exists (not a river junction).
     */
    private static WallColumn measure(WorldGenLevel level, BlockPos.MutableBlockPos pos,
                                      int x, int z, int minY, int maxY) {
        int waterLine = Integer.MIN_VALUE;

        for (int[] dir : NEIGHBOR_OFFSETS) {
            for (int y = maxY + WATER_PROBE_ABOVE; y >= maxY - WATER_PROBE_BELOW; y--) {
                pos.set(x + dir[0], y, z + dir[1]);
                if (isWaterOrIce(level.getBlockState(pos))) {
                    if (y > waterLine) {
                        waterLine = y;
                    }
                    break;
                }
            }
        }

        if (waterLine == Integer.MIN_VALUE) {
            return null;
        }

        // Snapshot taken before any writes, so an opened run cannot enable its neighbour.
        boolean[] waterBehind = new boolean[NEIGHBOR_OFFSETS.length];
        for (int i = 0; i < NEIGHBOR_OFFSETS.length; i++) {
            pos.set(x + NEIGHBOR_OFFSETS[i][0], waterLine, z + NEIGHBOR_OFFSETS[i][1]);
            waterBehind[i] = isWaterOrIce(level.getBlockState(pos));
        }

        return new WallColumn(x, z, minY, maxY, waterLine, waterBehind);
    }

    /**
     * Pass B: opens the first qualifying run for this column. A run opens only if every block above
     * H in all its columns can also be cleared.
     *
     * @return {openedBlocks, removedAbove, apronBlocks} when opened, {-1, 0, 0} when the run had too
     *         much rock above to open, or null when no direction qualified.
     */
    private static int[] tryOpenLip(WorldGenLevel level, BlockPos.MutableBlockPos pos, WallColumn column) {
        int x = column.x();
        int z = column.z();
        int h = column.waterLine();

        for (int i = 0; i < NEIGHBOR_OFFSETS.length; i++) {
            if (!column.waterBehind()[i]) {
                continue;
            }
            int[] dir = NEIGHBOR_OFFSETS[i];
            // The pool is at +dir, so the run extends along -dir from this column.
            int[] run = {-dir[0], -dir[1]};

            for (int runLength = 1; runLength <= MAX_CARVE_THICKNESS; runLength++) {
                if (!isSolidRun(level, pos, x, h, z, run, runLength)) {
                    break;
                }
                pos.set(x + run[0] * runLength, h, z + run[1] * runLength);
                BlockState beyond = level.getBlockState(pos);
                if (!beyond.isAir() && !isWaterOrIce(beyond)) {
                    continue; // still wall; try one block thicker
                }

                int clearance = solidAbove(level, pos, x, h, z, run, runLength);
                if (clearance < 0) {
                    return new int[]{-1, 0, 0}; // a bank or hillside, not a wall
                }

                pos.set(x + dir[0], h, z + dir[1]);
                BlockState fluid = level.getBlockState(pos);
                if (!isWaterOrIce(fluid)) {
                    continue; // pool changed since the snapshot
                }

                // Read before openRun replaces the top of the span with water.
                BlockState sillMaterial = wallMaterial(level, pos, column);

                int removed = openRun(level, pos, x, h, z, run, runLength, fluid);
                int apron = sillMaterial == null
                        ? 0
                        : buildUpstreamApron(level, pos, x, h, z, dir, sillMaterial);
                return new int[]{runLength, removed, apron};
            }
        }
        return null;
    }

    /**
     * Counts solid blocks above H across the run, each column up to its first non-solid block.
     *
     * @return the total, or -1 if any column stays solid up to BARRIER_MAX_Y (a bank, not a wall).
     */
    private static int solidAbove(WorldGenLevel level, BlockPos.MutableBlockPos pos,
                                  int x, int h, int z, int[] run, int runLength) {
        int total = 0;
        for (int offset = 0; offset < runLength; offset++) {
            int wx = x + run[0] * offset;
            int wz = z + run[1] * offset;
            boolean reachedAir = false;
            for (int y = h + 1; y <= SeaLevelBandingService.BARRIER_MAX_Y; y++) {
                pos.set(wx, y, wz);
                if (!isSolid(level.getBlockState(pos))) {
                    reachedAir = true;
                    break;
                }
                total++;
            }
            if (!reachedAir) {
                return -1;
            }
        }
        return total;
    }

    /**
     * Sets each run column at y == H to the pool's fluid (scheduling a fluid tick, since setBlock
     * alone never makes it flow) and clears the solid blocks above. H - 1 stays as the sill.
     *
     * @return how many blocks were removed above H.
     */
    private static int openRun(WorldGenLevel level, BlockPos.MutableBlockPos pos,
                               int x, int h, int z, int[] run, int runLength, BlockState fluid) {
        int removed = 0;

        for (int offset = 0; offset < runLength; offset++) {
            int wx = x + run[0] * offset;
            int wz = z + run[1] * offset;

            pos.set(wx, h, wz);
            level.setBlock(pos, fluid, Block.UPDATE_ALL);
            if (!fluid.getFluidState().isEmpty()) {
                // Immutable copy: the scheduled tick must not share the reused mutable pos.
                level.scheduleTick(pos.immutable(), fluid.getFluidState().getType(), 0);
            }

            for (int y = h + 1; y <= SeaLevelBandingService.BARRIER_MAX_Y; y++) {
                pos.set(wx, y, wz);
                if (!isSolid(level.getBlockState(pos))) {
                    break;
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Extends the sill one layer at y == H - 1 up to LIP_UPSTREAM_REACH blocks into the upper pool,
     * stopping where y == H is no longer water and skipping positions already solid.
     *
     * @return how many blocks of shelf were placed.
     */
    private static int buildUpstreamApron(WorldGenLevel level, BlockPos.MutableBlockPos pos,
                                          int x, int h, int z, int[] dir, BlockState material) {
        int placed = 0;
        int crest = h - 1;

        for (int step = 1; step <= LIP_UPSTREAM_REACH; step++) {
            int wx = x + dir[0] * step;
            int wz = z + dir[1] * step;

            pos.set(wx, h, wz);
            if (!isWaterOrIce(level.getBlockState(pos))) {
                break; // left the pool
            }

            pos.set(wx, crest, wz);
            if (isSolid(level.getBlockState(pos))) {
                continue;
            }
            level.setBlock(pos, material, Block.UPDATE_ALL);
            placed++;
        }
        return placed;
    }

    /**
     * The column's wall material: the first solid block scanning down the recorded span, or null
     * if there is none (the column is then left alone rather than sealed with water or air).
     */
    private static BlockState wallMaterial(WorldGenLevel level, BlockPos.MutableBlockPos pos, WallColumn column) {
        for (int y = column.maxY(); y >= column.minY(); y--) {
            pos.set(column.x(), y, column.z());
            BlockState state = level.getBlockState(pos);
            if (isSolid(state)) {
                return state;
            }
        }
        return null;
    }

    /**
     * Pass C: fills every non-solid block from H - 1 down, continuing through the recorded span and
     * stopping at the first solid block below it (the river bed).
     *
     * @return how many blocks were sealed.
     */
    private static int sealDown(WorldGenLevel level, BlockPos.MutableBlockPos pos,
                                WallColumn column, BlockState fillMaterial) {
        int x = column.x();
        int z = column.z();
        int top = column.waterLine() - 1;
        int sealed = 0;
        // Defensive floor against a deep cave directly beneath a junction.
        int floor = Math.max(level.getMinY(), SeaLevelBandingService.BARRIER_MIN_Y);

        for (int y = top; y >= floor; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);

            if (isSolid(state)) {
                if (y >= column.minY()) {
                    continue; // part of the barrier itself
                }
                break; // river bed
            }

            level.setBlock(pos, fillMaterial, Block.UPDATE_ALL);
            sealed++;
        }
        return sealed;
    }

    /** True if every block from (x, z) out to runLength - 1 blocks along run is solid. */
    private static boolean isSolidRun(WorldGenLevel level, BlockPos.MutableBlockPos pos,
                                      int x, int h, int z, int[] run, int runLength) {
        for (int offset = 0; offset < runLength; offset++) {
            pos.set(x + run[0] * offset, h, z + run[1] * offset);
            if (!isSolid(level.getBlockState(pos))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Deterministic hashed lattice of RAPIDS_CELL_SIZE cells choosing which stretches of wall may
     * open; hashing avoids aligning with axis-parallel walls. floorDiv keeps cells uniform across 0.
     */
    private static boolean isRapidsOpening(int x, int z) {
        int h = Math.floorDiv(x, RAPIDS_CELL_SIZE) * 0x27D4EB2D
                ^ Math.floorDiv(z, RAPIDS_CELL_SIZE) * 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return Math.floorMod(h, 8) < RAPIDS_OPEN_IN_EIGHT;
    }

    /** Solid for this feature: not air, water, ice or any fluid. */
    private static boolean isSolid(BlockState state) {
        return !state.isAir() && !isWaterOrIce(state) && state.getFluidState().isEmpty();
    }

    private static boolean isWaterOrIce(BlockState state) {
        return state.is(Blocks.WATER)
                || state.is(Blocks.ICE)
                || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.FROSTED_ICE);
    }
}
