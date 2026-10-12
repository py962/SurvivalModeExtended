package com.terrainlab.temperature;

import com.terrainlab.TerrainLab;
import com.terrainlab.season.SeasonalBiomeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Set;

/**
 * Blends the full 8-point SeasonalBiomeData profile of nearby columns, distance-weighted, so transitions stay
 * smooth in every season. Biomes in BiomeTemperatureTable.INHERITS_SURROUNDINGS contribute no sample and so take
 * on their neighbours' values; if a whole neighbourhood is such biomes, the search radius widens.
 */
public final class BiomeTemperatureBlender {

    /** Matches vanilla's 4-block biome storage granularity; finer sampling finds nothing new. */
    private static final int SAMPLE_SPACING = 4;

    /** Widest ring-search radius (blocks); hitting it usually means a biome is missing from the table. */
    private static final int MAX_BLEND_RADIUS = 48;

    // Read from the server and client render threads, so it must be thread-safe.
    private static final Set<Identifier> loggedMissingBiomes = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private BiomeTemperatureBlender() {
    }

    /**
     * Distance-weighted blend of profiles within blendRadius of pos, widening the radius up to MAX_BLEND_RADIUS
     * when no sample has table data. Returns null if nothing is found.
     */
    public static SeasonalBiomeData blend(LevelReader level, BlockPos pos, int blendRadius) {
        int radius = Math.max(SAMPLE_SPACING, blendRadius);

        while (true) {
            SeasonalBiomeData result = blendAtRadius(level, pos, radius);
            if (result != null) {
                return result;
            }

            if (radius >= MAX_BLEND_RADIUS) {
                return null;
            }

            radius = Math.min(MAX_BLEND_RADIUS, radius + SAMPLE_SPACING * 2);
        }
    }

    private static SeasonalBiomeData blendAtRadius(LevelReader level, BlockPos pos, int blendRadius) {
        double[] accum = new double[8];
        double totalWeight = 0.0;

        // Precipitation is categorical, so it is taken from the nearest sample with table data.
        double closestPrecipDistance = Double.MAX_VALUE;
        SeasonalBiomeData.PrecipitationBehaviour closestPrecip = SeasonalBiomeData.PrecipitationBehaviour.NONE;

        int steps = blendRadius / SAMPLE_SPACING;

        for (int ix = -steps; ix <= steps; ix++) {
            int dx = ix * SAMPLE_SPACING;
            for (int iz = -steps; iz <= steps; iz++) {
                int dz = iz * SAMPLE_SPACING;

                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > blendRadius) continue;

                double weight = (blendRadius - distance) / blendRadius;
                if (weight <= 0.0) continue;

                BlockPos samplePos = pos.offset(dx, 0, dz);
                SeasonalBiomeData data = sampleBiomeData(level, samplePos);
                if (data == null) continue; // skip rather than dilute the average

                if (distance < closestPrecipDistance) {
                    closestPrecipDistance = distance;
                    closestPrecip = data.precipitation();
                }

                accum[0] += data.midwinterDay() * weight;
                accum[1] += data.midwinterNight() * weight;
                accum[2] += data.nearWinterDay() * weight;
                accum[3] += data.nearWinterNight() * weight;
                accum[4] += data.nearSummerDay() * weight;
                accum[5] += data.nearSummerNight() * weight;
                accum[6] += data.midsummerDay() * weight;
                accum[7] += data.midsummerNight() * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight <= 0.0) {
            return null; // blend() decides whether to widen
        }

        return new SeasonalBiomeData(
                accum[0] / totalWeight, accum[1] / totalWeight,
                accum[2] / totalWeight, accum[3] / totalWeight,
                accum[4] / totalWeight, accum[5] / totalWeight,
                accum[6] / totalWeight, accum[7] / totalWeight,
                closestPrecip
        );
    }

    private static SeasonalBiomeData sampleBiomeData(LevelReader level, BlockPos pos) {
        Identifier biomeId = getBiomeId(level, pos);
        if (biomeId == null) return null;

        if (BiomeTemperatureTable.INHERITS_SURROUNDINGS.contains(biomeId)) {
            return null; // takes its neighbours' values via the blend
        }

        SeasonalBiomeData data = BiomeTemperatureTable.get(biomeId);
        if (data == null && loggedMissingBiomes.add(biomeId)) {
            // Once per biome ID.
            TerrainLab.LOGGER.warn("No SeasonalBiomeData for biome {} - excluded from temperature blending", biomeId);
        }
        return data;
    }

    private static Identifier getBiomeId(LevelReader level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        return biomeHolder.unwrapKey()
                .map(ResourceKey::identifier)
                .orElse(null);
    }

    /**
     * Last-resort fallback when blend() finds nothing: maps the world's raw climate temperature noise
     * (NoiseRouter#temperature, F3 "T:") from roughly [-1, 1] to roughly [-15C, 35C] as a flat profile.
     * Server-only, since RandomState does not exist on a client; returns null for any non-ServerLevel.
     * <p>TODO: calibrate the mapping against BiomeTemperatureTable.
     */
    public static SeasonalBiomeData climateNoiseFallback(LevelReader level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        RandomState randomState = serverLevel.getChunkSource().randomState();
        NoiseRouter router = randomState.router();
        DensityFunction.SinglePointContext ctx =
                new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ());
        double rawTemp = router.temperature().compute(ctx);

        double celsius = rawTemp * 25.0 + 10.0;

        return new SeasonalBiomeData(
                celsius, celsius, celsius, celsius, celsius, celsius, celsius, celsius,
                SeasonalBiomeData.PrecipitationBehaviour.NONE);
    }
}
