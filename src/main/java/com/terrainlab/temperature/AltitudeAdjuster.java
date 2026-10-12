package com.terrainlab.temperature;

import com.terrainlab.season.SeasonalBiomeData;

/**
 * Cools a blended biome profile by 0.1C per block above sea level (Y64), capped at a 35C total drop.
 * No adjustment is applied below sea level; cave biomes carry their own values in BiomeTemperatureTable.
 */
public final class AltitudeAdjuster {

    public static final int REFERENCE_SEA_LEVEL = 64;
    public static final double COOLING_PER_BLOCK = 0.1; // C per block
    public static final double MAX_TOTAL_DROP = 35.0; // C

    private AltitudeAdjuster() {
    }

    public static SeasonalBiomeData apply(SeasonalBiomeData blended, int y) {
        if (y <= REFERENCE_SEA_LEVEL) {
            return blended;
        }
        double rawDrop = (y - REFERENCE_SEA_LEVEL) * COOLING_PER_BLOCK;
        double delta = -Math.min(rawDrop, MAX_TOTAL_DROP);

        return new SeasonalBiomeData(
                blended.midwinterDay() + delta, blended.midwinterNight() + delta,
                blended.nearWinterDay() + delta, blended.nearWinterNight() + delta,
                blended.nearSummerDay() + delta, blended.nearSummerNight() + delta,
                blended.midsummerDay() + delta, blended.midsummerNight() + delta,
                blended.precipitation()
        );
    }
}
