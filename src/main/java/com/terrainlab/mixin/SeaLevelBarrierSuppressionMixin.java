package com.terrainlab.mixin;

import com.mojang.logging.LogUtils;
import com.terrainlab.worldgen.AquiferBarrierRecorder;
import com.terrainlab.worldgen.SeaLevelBandingService;
import com.terrainlab.worldgen.SeaLevelRandomStateHolder;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

/**
 * Sea-level banding support inside vanilla's aquifer. Where continentalness bands with different water levels
 * meet, NoiseBasedAquifer places a barrier (computeSubstance returns null). These barriers are recorded so
 * SeaLevelBarrierGapFeature can extend the wall down to the river bed and open regular gaps at the water line.
 * <ul>
 *   <li>Mechanism 3 (active): terrainlab$observeVanillaBarrier records every observed barrier block in
 *   AquiferBarrierRecorder. Barriers form only where two aquifer cells disagree, so a column's recorded span
 *   gives the higher water level (maxY) and the lower one (minY - 1) without consulting noise.</li>
 *   <li>Mechanism 1 (disabled, WALL_TIER_MATH_ENABLED): predicted walls from a 1-block neighbour tier
 *   comparison, which misses barriers vanilla places on its ~16-block cell lattice.</li>
 *   <li>Mechanism 2 (disabled, GAP_FORCING_ENABLED): forced water gaps at predicted boundaries.</li>
 * </ul>
 * The disabled mechanisms are kept as rollback paths.
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class SeaLevelBarrierSuppressionMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/SeaLevelBanding]";

    // Gap lattice for Mechanism 2: a GAP_WIDTH-wide opening every SPACING blocks on each axis.
    private static final int SPACING = 12;
    private static final int GAP_WIDTH = 6;

    // Mechanism 2; gaps are cut by SeaLevelBarrierGapFeature from observed barriers.
    private static final boolean GAP_FORCING_ENABLED = false;

    // Mechanism 1; Mechanism 3 observations are the sole source of sub-water walls.
    private static final boolean WALL_TIER_MATH_ENABLED = false;

    // Per-block logging for Mechanisms 1 and 2; at real scale it stalls worldgen threads.
    private static final boolean VERBOSE_BOUNDARY_LOGGING = false;

    // Per-column logging for Mechanism 3.
    private static final boolean VERBOSE_OBSERVED_BARRIER_LOGGING = false;

    // Per-aquifer (i.e. per-chunk) cache of banded levels, keyed by ColumnPos.asLong.
    @Unique
    private final Long2IntOpenHashMap terrainlab$levelCache = new Long2IntOpenHashMap();

    // Per-aquifer dedupe so Mechanism 3 logs at most once per column.
    @Unique
    private final Set<Integer> terrainlab$loggedObservedBarrierColumns = new HashSet<>();

    /** Mechanism 2 (disabled): forces water at the band transition in gap columns before vanilla runs. */
    @Inject(
            method = "computeSubstance",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void terrainlab$onComputeSubstanceHead(
            DensityFunction.FunctionContext context,
            double density,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (!GAP_FORCING_ENABLED) {
            return;
        }

        int x = context.blockX();
        int z = context.blockZ();

        if (!isInGap(x, z)) {
            return;
        }

        // Only force into positions vanilla treats as open: banded levels exist everywhere, even under mountains.
        if (density > 0.0) {
            return;
        }

        RandomState randomState = SeaLevelRandomStateHolder.get();
        if (randomState == null) {
            return; // not cached yet during the first chunks of a world load
        }

        DensityFunction continentalness = randomState.router().continents();
        DensityFunction erosion = randomState.router().erosion();

        int here = terrainlab$cachedBandedLevel(continentalness, erosion, x, z);
        int east = terrainlab$cachedBandedLevel(continentalness, erosion, x + 1, z);
        int south = terrainlab$cachedBandedLevel(continentalness, erosion, x, z + 1);

        if (here == east && here == south) {
            return; // not a boundary column
        }

        int lower = Math.min(here, Math.min(east, south));
        int upper = Math.max(here, Math.max(east, south));

        if (upper - lower > 1) {
            return;
        }

        int y = context.blockY();

        if (y < lower || y > upper) {
            return; // outside the transition band
        }

        if (VERBOSE_BOUNDARY_LOGGING) {
            // LOGGER.info("{} preemptively forcing GAP open at ({}, {}, {}): band=[{},{}] (computed directly, not via calculatePressure)",
                    // LOG_PREFIX, x, y, z, lower, upper);
        }
        cir.setReturnValue(Blocks.WATER.defaultBlockState());
    }

    /** Same computation as InlandRiverFloodFeature.bandedLevelAt, so both agree on transition positions. */
    @Unique
    private static int bandedLevelAt(DensityFunction continentalness, DensityFunction erosion, int x, int z) {
        DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(x, 0, z);
        double continentalnessSample = continentalness.compute(context);
        double erosionSample = erosion.compute(context);
        double combinedScore = continentalnessSample - erosionSample;
        return SeaLevelBandingService.bandedLevel(continentalnessSample, combinedScore);
    }

    /** Cached {@link #bandedLevelAt}. */
    @Unique
    private int terrainlab$cachedBandedLevel(DensityFunction continentalness, DensityFunction erosion, int x, int z) {
        long key = ColumnPos.asLong(x, z);
        if (terrainlab$levelCache.containsKey(key)) {
            return terrainlab$levelCache.get(key);
        }
        int level = bandedLevelAt(continentalness, erosion, x, z);
        terrainlab$levelCache.put(key, level);
        return level;
    }

    /**
     * Mechanism 1 (disabled): on the high side of a band boundary, replaces water strictly below the column's
     * own level with null (local terrain), extending the wall down to the river bed. The shelf at
     * {@code here - 1} is left to SeaLevelBarrierGapFeature.
     */
    @Inject(
            method = "computeSubstance",
            at = @At("RETURN"),
            cancellable = true,
            require = 1
    )
    private void terrainlab$enforceBandingDecision(
            DensityFunction.FunctionContext context,
            double density,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (!WALL_TIER_MATH_ENABLED) {
            return;
        }

        BlockState result = cir.getReturnValue();
        if (result == null || !result.is(Blocks.WATER)) {
            return; // vanilla isn't placing water here - nothing to suppress
        }

        RandomState randomState = SeaLevelRandomStateHolder.get();
        if (randomState == null) {
            return; // not cached yet during the first chunks of a world load
        }

        DensityFunction continentalness = randomState.router().continents();
        DensityFunction erosion = randomState.router().erosion();

        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();

        int here = terrainlab$cachedBandedLevel(continentalness, erosion, x, z);
        int east = terrainlab$cachedBandedLevel(continentalness, erosion, x + 1, z);
        int west = terrainlab$cachedBandedLevel(continentalness, erosion, x - 1, z);
        int south = terrainlab$cachedBandedLevel(continentalness, erosion, x, z + 1);
        int north = terrainlab$cachedBandedLevel(continentalness, erosion, x, z - 1);

        int lowestNeighbor = Math.min(Math.min(east, west), Math.min(south, north));
        if (here <= lowestNeighbor) {
            return; // low/receiving side, or not a boundary
        }

        // Suppress strictly below this column's own surface; the surface shelf belongs to SeaLevelBarrierGapFeature.
        int barrierY = here - 1;

        if (y == barrierY && VERBOSE_BOUNDARY_LOGGING) {
            // LOGGER.info("{} AT-OWN-LEVEL ({}, {}, {}) result=WATER, left unsuppressed (barrierY={}, lowestNeighbor={})",
                    // LOG_PREFIX, x, y, z, barrierY, lowestNeighbor);
        }

        if (y >= barrierY) {
            return;
        }

        // null is vanilla's "use the terrain's own block" signal, so the wall is built from local rock and ends
        // where vanilla's own decision stops being water (the river bed).
        if (VERBOSE_BOUNDARY_LOGGING) {
            // LOGGER.info("{} suppressing water at ({}, {}, {}), deferring to local terrain material (this column's own barrierY={}, lowestNeighbor={})",
                    // LOG_PREFIX, x, y, z, barrierY, lowestNeighbor);
        }
        cir.setReturnValue(null);
    }

    /**
     * Mechanism 3: records vanilla barrier placements. Under density &lt;= 0 every non-barrier return path
     * yields a fluid or air state, so a null result can only come from one of calculatePressure's barrier
     * branches. Only the banding Y window is recorded; barriers outside it are ordinary cave aquifer walls.
     * AquiferBarrierRecorder ignores calls outside real chunk generation (see NoiseFillContextMixin), so
     * aquifers built for height probes are not recorded.
     */
    @Inject(
            method = "computeSubstance",
            at = @At("RETURN"),
            require = 1
    )
    private void terrainlab$observeVanillaBarrier(
            DensityFunction.FunctionContext context,
            double density,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (density > 0.0) {
            return; // solid ground: the aquifer was not consulted
        }

        if (cir.getReturnValue() != null) {
            return; // non-null under density <= 0 is fluid or air, never a barrier
        }

        int y = context.blockY();
        if (y < SeaLevelBandingService.BARRIER_MIN_Y || y > SeaLevelBandingService.BARRIER_MAX_Y) {
            return; // ordinary deep aquifer barrier, not a sea-level junction
        }

        int x = context.blockX();
        int z = context.blockZ();

        AquiferBarrierRecorder.recordBarrier(x, y, z);

        // Off by default: ~26 columns per chunk; SeaLevelBarrierGapFeature already logs a per-chunk summary.
        if (VERBOSE_OBSERVED_BARRIER_LOGGING) {
            int columnKey = ((x & 15) << 4) | (z & 15);
            if (terrainlab$loggedObservedBarrierColumns.add(columnKey)) {
                // LOGGER.info("{} OBSERVED-BARRIER ({}, {}, {}) - first block seen in this column",
                        // LOG_PREFIX, x, y, z);
            }
        }
    }

    /** Mechanism 2 gap lattice: true where both axes are in their gap phase (25% of columns). */
    @Unique
    private static boolean isInGap(int x, int z) {
        boolean gapX = Math.floorMod(x, SPACING) < GAP_WIDTH;
        boolean gapZ = Math.floorMod(z, SPACING) < GAP_WIDTH;
        return gapX && gapZ;
    }
}
