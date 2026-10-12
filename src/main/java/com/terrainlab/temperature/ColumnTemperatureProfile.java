package com.terrainlab.temperature;

import com.terrainlab.season.SeasonalBiomeData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

/**
 * Column temperature profile: biome blend plus altitude adjustment, falling back to a flat 15C profile.
 */
public final class ColumnTemperatureProfile {

    /** Blocks. */
    public static final int DEFAULT_BLEND_RADIUS = 6;

    private ColumnTemperatureProfile() {
    }

    public static SeasonalBiomeData compute(LevelReader level, BlockPos pos) {
        return compute(level, pos, DEFAULT_BLEND_RADIUS);
    }

    public static SeasonalBiomeData compute(LevelReader level, BlockPos pos, int blendRadius) {
        SeasonalBiomeData blended = BiomeTemperatureBlender.blend(level, pos, blendRadius);
        if (blended == null) {
            return neutralFallback();
        }
        return AltitudeAdjuster.apply(blended, pos.getY());
    }

    private static SeasonalBiomeData neutralFallback() {
        return new SeasonalBiomeData(15, 15, 15, 15, 15, 15, 15, 15,
                SeasonalBiomeData.PrecipitationBehaviour.NONE);
    }
}
