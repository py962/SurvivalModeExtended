package com.terrainlab.worldgen;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;

/**
 * Aquifer.FluidPicker that bands the sea level by continentalness and erosion
 * (via SeaLevelBandingService) instead of returning one constant level.
 * <p>
 * One instance is shared by all worldgen threads for a RandomState, and
 * computeFluid runs once per block, so it uses a per-thread direct-mapped column
 * cache and preallocated FluidStatus instances to avoid locking and allocation.
 */
public final class BandedFluidPicker implements Aquifer.FluidPicker {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/SeaLevelBanding]";

    // Per-column trace is very high volume (~90 lines per chunk); enable only for debugging.
    private static final boolean VERBOSE_COLUMN_LOGGING = false;

    private static final int LAVA_FLOOR_Y = -54;
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();

    private final DensityFunction continentalness;
    private final DensityFunction erosion;
    private final BlockState defaultFluid;
    private final int lavaThresholdY;

    /** Per-thread, per-picker column cache of banded sea levels. */
    private final ThreadLocal<ColumnCache> columnLevelCache = ThreadLocal.withInitial(ColumnCache::new);

    /**
     * Direct-mapped cache of 2^CACHE_BITS slots indexed by a Fibonacci hash of the
     * column key; each slot stores its full key. Thread-confined.
     */
    private static final class ColumnCache {
        private static final int CACHE_BITS = 14;
        private static final int EMPTY = Integer.MIN_VALUE;
        private final long[] keys = new long[1 << CACHE_BITS];
        private final int[] values = new int[1 << CACHE_BITS];

        ColumnCache() {
            java.util.Arrays.fill(this.values, EMPTY);
        }

        static int slot(long key) {
            return (int) ((key * 0x9E3779B97F4A7C15L) >>> (64 - CACHE_BITS));
        }
    }

    private final Aquifer.FluidStatus lavaStatus;

    // One shared, immutable FluidStatus per possible banded level, indexed by level - BASE_SEA_LEVEL.
    private final Aquifer.FluidStatus[] statusByLevel;

    public BandedFluidPicker(DensityFunction continentalness, DensityFunction erosion, NoiseGeneratorSettings settings) {
        this.continentalness = continentalness;
        this.erosion = erosion;
        this.defaultFluid = settings.defaultFluid();
        this.lavaThresholdY = Math.min(LAVA_FLOOR_Y, SeaLevelBandingService.BASE_SEA_LEVEL);
        this.lavaStatus = new Aquifer.FluidStatus(LAVA_FLOOR_Y, LAVA);

        int levels = SeaLevelBandingService.MAX_BANDED_LEVEL - SeaLevelBandingService.BASE_SEA_LEVEL + 1;
        this.statusByLevel = new Aquifer.FluidStatus[levels];
        for (int i = 0; i < levels; i++) {
            this.statusByLevel[i] = new Aquifer.FluidStatus(SeaLevelBandingService.BASE_SEA_LEVEL + i, this.defaultFluid);
        }

        if (VERBOSE_COLUMN_LOGGING) {
            // LOGGER.debug("{} BandedFluidPicker instance created (defaultFluid={}, lavaThresholdY={})",
                    // LOG_PREFIX, this.defaultFluid, this.lavaThresholdY);
        }
    }

    public static BandedFluidPicker fromRandomState(RandomState randomState, NoiseGeneratorSettings settings) {
        return new BandedFluidPicker(randomState.router().continents(), randomState.router().erosion(), settings);
    }

    @Override
    public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
        if (y < this.lavaThresholdY) {
            return this.lavaStatus;
        }

        int bandedLevel = this.levelForColumn(x, z);
        int index = bandedLevel - SeaLevelBandingService.BASE_SEA_LEVEL;
        if (index < 0 || index >= this.statusByLevel.length) {
            // Defensive: a banded level outside BASE_SEA_LEVEL..MAX_BANDED_LEVEL.
            return new Aquifer.FluidStatus(bandedLevel, this.defaultFluid);
        }
        return this.statusByLevel[index];
    }

    private int levelForColumn(int x, int z) {
        long key = ColumnPos.asLong(x, z);
        ColumnCache cache = this.columnLevelCache.get();
        int slot = ColumnCache.slot(key);
        int cached = cache.values[slot];
        if (cached != ColumnCache.EMPTY && cache.keys[slot] == key) {
            return cached;
        }
        int level = this.computeLevelForColumn(x, z);
        cache.keys[slot] = key;
        cache.values[slot] = level;
        return level;
    }

    private int computeLevelForColumn(int x, int z) {
        {
            DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(x, 0, z);
            double continentalnessSample = this.continentalness.compute(context);
            double erosionSample = this.erosion.compute(context);
            double combinedScore = continentalnessSample - erosionSample;

            int level = SeaLevelBandingService.bandedLevel(continentalnessSample, combinedScore);

            if (VERBOSE_COLUMN_LOGGING && level != SeaLevelBandingService.BASE_SEA_LEVEL) {
                // LOGGER.debug("{} column ({}, {}) banded: continentalness={}, erosion={}, combinedScore={}, seaLevel={}",
                        // LOG_PREFIX, x, z, continentalnessSample, erosionSample, combinedScore, level);
            }

            return level;
        }
    }
}
