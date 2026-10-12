
package com.terrainlab.temperature;

import com.terrainlab.celestial.CelestialConduitRegistry;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.season.SeasonalBiomeData;
import com.terrainlab.season.TemperatureSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;

public final class LocalTemperatureService {

    private static final double SHIELD_TARGET_TEMPERATURE = 20.0;

    private LocalTemperatureService() {
    }

    public static double getAmbientTemperature(
            Level level,
            BlockPos pos,
            TemperatureSeason season,
            boolean isDay
    ) {
        return getAmbientTemperature(level, pos, season, isDay ? 0.0 : 1.0);
    }

    /**
     * Ambient temperature with day and night blended by nightFraction
     * (0 = day, 1 = night); see SolarTime#nightFraction.
     */
    public static double getAmbientTemperature(
            Level level,
            BlockPos pos,
            TemperatureSeason season,
            double nightFraction
    ) {
        SeasonalBiomeData blended = getBlendedProfile(level, pos);
        SeasonalBiomeData adjusted = AltitudeAdjuster.apply(blended, pos.getY());
        double outdoorTemp = adjusted.getTemperature(season, nightFraction);

        // Bypass the shelter/exposure model entirely when submerged.
        boolean submerged = !level.getFluidState(pos).isEmpty();

        double exposureAdjusted;
        if (submerged) {
            exposureAdjusted = outdoorTemp;
        } else {
            double exposure = SkyExposureService.getExposure(level, pos);

            // A sandstorm blocks the sun like rain; arid biomes never count as raining.
            boolean isRaining = level.isRainingAt(pos)
                    || com.terrainlab.weather.Sandstorm.isActiveAt(level, pos);
            boolean hasSkyLight = level.dimensionType().hasSkyLight();

            exposureAdjusted = SkyExposureEffect.apply(
                    outdoorTemp,
                    exposure,
                    nightFraction,
                    isRaining,
                    hasSkyLight
            );
        }

        double heatContribution = HeatContributionService.getContribution(level, pos);
        double ambient = exposureAdjusted + heatContribution;

        // Shield conduits stabilise ambient environmental temperature towards 20°C.
        // Query the existing registry rather than scanning the surrounding world.
        if (level instanceof ServerLevel serverLevel) {
            ambient = applyShieldTemperatureStabilisation(serverLevel, pos, ambient);
        }

        return ambient;
    }

    /**
     * Applies the strongest active Shield conduit effect at this position.
     *
     * <p>The temperature approaches 20°C linearly with proximity to the conduit:
     * it reaches 20°C at the conduit position and has no effect at the radius edge.
     * Outside all active Shield radii, the ambient temperature is unchanged.
     */
    private static double applyShieldTemperatureStabilisation(
            ServerLevel level,
            BlockPos pos,
            double ambient
    ) {
        var conduit = CelestialConduitRegistry.get(level)
                .findStrongestActiveNearby(
                        CelestialVariant.SHIELD,
                        level.dimension(),
                        pos
                );

        if (conduit.isEmpty()) {
            return ambient;
        }

        var record = conduit.get();
        double radius = record.effectRadius();

        if (radius <= 0.0) {
            return ambient;
        }

        double distance = Math.sqrt(record.pos().distSqr(pos));
        double strength = Math.max(0.0, Math.min(1.0, 1.0 - distance / radius));

        return ambient + (SHIELD_TARGET_TEMPERATURE - ambient) * strength;
    }

    /**
     * Neighbour-blended seasonal profile for this column, cached per chunk column.
     * Also used by SeasonalPrecipitation for biomes that inherit their surroundings' weather.
     */
    public static SeasonalBiomeData getBlendedProfile(LevelReader level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(pos);
        ChunkTemperatureCache cache =
                chunk.getData(ModAttachments.CHUNK_TEMPERATURE_CACHE.get());

        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;

        SeasonalBiomeData cached = cache.get(localX, localZ);
        if (cached != null) {
            return cached;
        }

        SeasonalBiomeData computed =
                BiomeTemperatureBlender.blend(
                        level,
                        pos,
                        ColumnTemperatureProfile.DEFAULT_BLEND_RADIUS
                );

        if (computed == null) {
            // Fall back to climate noise (server only; null on a remote client).
            computed = BiomeTemperatureBlender.climateNoiseFallback(level, pos);
        }

        if (computed == null) {
            // Not cached: the cache uses null for "unfilled", so this column is retried next time.
            return neutralFallback();
        }

        cache.set(localX, localZ, computed);
        return computed;
    }

    private static SeasonalBiomeData neutralFallback() {
        return new SeasonalBiomeData(
                15, 15, 15, 15,
                15, 15, 15, 15,
                SeasonalBiomeData.PrecipitationBehaviour.NONE
        );
    }
}