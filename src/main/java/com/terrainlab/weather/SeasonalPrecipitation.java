
package com.terrainlab.weather;

import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonalBiomeData;
import com.terrainlab.season.VisualSeason;
import com.terrainlab.temperature.BiomeTemperatureTable;
import com.terrainlab.temperature.LocalTemperatureService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import static com.terrainlab.season.SeasonalBiomeData.PrecipitationBehaviour.NONE;
import static com.terrainlab.season.SeasonalBiomeData.PrecipitationBehaviour.SEASONAL_WET_DRY;

public final class SeasonalPrecipitation {

    private static final int PRECIPITATION_SAMPLE_SPACING = 4;
    private static final int MAX_PRECIPITATION_SEARCH_RADIUS = 48;

    private SeasonalPrecipitation() {
    }

    /**
     * Returns the local precipitation type, or null if the biome has no
     * TerrainLab seasonal temperature data and should use vanilla behaviour.
     *
     * River biomes inherit the precipitation probability decision from the
     * nearest surrounding biome with seasonal data.
     */
    public static Biome.Precipitation decide(
            Level level,
            BlockPos pos,
            Season season,
            boolean isDay,
            boolean globallyRaining
    ) {
        SeasonalBiomeData data = lookup(level, pos);
        if (data == null) {
            return null;
        }

        SeasonalBiomeData.PrecipitationBehaviour behaviour = data.precipitation();

        if (behaviour == NONE) {
            return Biome.Precipitation.NONE;
        }

        // Savannas retain their extreme seasonal wet/dry behaviour.
        if (behaviour == SEASONAL_WET_DRY
                && isDrySeason(season.visualSeason())) {
            return Biome.Precipitation.NONE;
        }

        // All local precipitation requires the shared weather to be rainy.
        if (!globallyRaining) {
            return Biome.Precipitation.NONE;
        }

        Identifier biomeId = getBiomeId(level, pos);
        if (biomeId == null) {
            return null;
        }

        Identifier precipitationBiomeId = biomeId;

        // River temperature is blended from its surroundings, but its
        // precipitation roll must also follow a surrounding biome.
        if (isInheritedRiver(biomeId)) {
            Identifier surroundingBiomeId =
                    findNearestPrecipitationBiome(level, pos);

            if (surroundingBiomeId != null) {
                precipitationBiomeId = surroundingBiomeId;
            }
        }

        double localPercent =
                BiomeTemperatureTable.getLocalPrecipitationPercent(
                        precipitationBiomeId,
                        season.visualSeason()
                );

        if (!passesLocalPrecipitationRoll(
                level,
                precipitationBiomeId,
                season,
                localPercent
        )) {
            return Biome.Precipitation.NONE;
        }

        // Keep the river's blended temperature profile for deciding
        // whether precipitation falls as rain or snow.
        return rainOrSnow(level, pos, season, isDay);
    }

    /**
     * Identifies river biomes that inherit their surrounding climate.
     */
    private static boolean isInheritedRiver(Identifier biomeId) {
        return biomeId.equals(
                Identifier.withDefaultNamespace("river"))
                || biomeId.equals(
                Identifier.withDefaultNamespace("frozen_river"))
                || biomeId.equals(
                Identifier.fromNamespaceAndPath(
                        "terrainlab",
                        "inland_river"
                ));
    }

    /**
     * Finds the nearest surrounding biome with seasonal table data.
     *
     * Samples at the same 4-block spacing and searches out to 48 blocks,
     * matching BiomeTemperatureBlender's sampling strategy. Biomes that
     * inherit their surroundings are excluded from the candidate samples.
     */
    private static Identifier findNearestPrecipitationBiome(
            Level level,
            BlockPos pos
    ) {
        for (int radius = PRECIPITATION_SAMPLE_SPACING;
             radius <= MAX_PRECIPITATION_SEARCH_RADIUS;
             radius += PRECIPITATION_SAMPLE_SPACING) {

            Identifier nearestBiomeId = null;
            double nearestDistanceSquared = Double.MAX_VALUE;

            for (int dx = -radius; dx <= radius;
                 dx += PRECIPITATION_SAMPLE_SPACING) {

                for (int dz = -radius; dz <= radius;
                     dz += PRECIPITATION_SAMPLE_SPACING) {

                    double distanceSquared =
                            (double) dx * dx + (double) dz * dz;

                    if (distanceSquared > (double) radius * radius
                            || distanceSquared >= nearestDistanceSquared) {
                        continue;
                    }

                    BlockPos samplePos = pos.offset(dx, 0, dz);
                    Identifier sampleBiomeId = getBiomeId(level, samplePos);

                    if (sampleBiomeId == null) {
                        continue;
                    }

                    if (BiomeTemperatureTable.INHERITS_SURROUNDINGS
                            .contains(sampleBiomeId)) {
                        continue;
                    }

                    if (BiomeTemperatureTable.get(sampleBiomeId) == null) {
                        continue;
                    }

                    nearestDistanceSquared = distanceSquared;
                    nearestBiomeId = sampleBiomeId;
                }
            }

            // The first radius containing a candidate has the nearest
            // eligible sample because all smaller radii were searched first.
            if (nearestBiomeId != null) {
                return nearestBiomeId;
            }
        }

        // No eligible surrounding biome was found.
        return null;
    }

    /**
     * Seasonal equivalent of Biome#shouldFreeze.
     * Returns null for biomes without TerrainLab temperature data.
     */
    public static Boolean shouldFreeze(
            Level level,
            BlockPos pos,
            Season season,
            boolean isDay
    ) {
        if (lookup(level, pos) == null) {
            return null;
        }

        if (isWarmEnough(level, pos, season, isDay)) {
            return false;
        }

        if (!level.isInsideBuildHeight(pos.getY())
                || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }

        BlockState blockState = level.getBlockState(pos);
        FluidState fluidState = level.getFluidState(pos);

        if (fluidState.getType() != Fluids.WATER
                || !(blockState.getBlock() instanceof LiquidBlock)) {
            return false;
        }

        boolean surroundedByWater = level.isWaterAt(pos.west())
                && level.isWaterAt(pos.east())
                && level.isWaterAt(pos.north())
                && level.isWaterAt(pos.south());

        return !surroundedByWater;
    }

    /**
     * Seasonal equivalent of Biome#shouldSnow.
     * Returns null for biomes without TerrainLab temperature data.
     */
    public static Boolean shouldSnow(
            Level level,
            BlockPos pos,
            Season season,
            boolean isDay,
            boolean globallyRaining
    ) {
        if (lookup(level, pos) == null) {
            return null;
        }

        if (decide(level, pos, season, isDay, globallyRaining)
                != Biome.Precipitation.SNOW) {
            return false;
        }

        if (!level.isInsideBuildHeight(pos.getY())
                || level.getBrightness(LightLayer.BLOCK, pos) >= 10) {
            return false;
        }

        BlockState blockState = level.getBlockState(pos);

        return (blockState.isAir() || blockState.is(Blocks.SNOW))
                && Blocks.SNOW.defaultBlockState().canSurvive(level, pos);
    }

    /**
     * Produces a stable precipitation roll for each in-game day and season,
     * shared by every biome: the day's roll is compared with each biome's own
     * chance. Neighbouring biomes with similar chances therefore rain together,
     * and differ only when their climates really do (a desert beside plains,
     * a savanna in its dry season). The biome id is kept in the signature for
     * the callers but no longer feeds the roll.
     */
    private static boolean passesLocalPrecipitationRoll(
            Level level,
            Identifier biomeId,
            Season season,
            double percent
    ) {
        double clampedPercent = Math.max(0.0, Math.min(100.0, percent));

        if (clampedPercent <= 0.0) {
            return false;
        }

        if (clampedPercent >= 100.0) {
            return true;
        }

        long day = Math.floorDiv(level.getGameTime(), 24_000L);

        long value = (level.dimension().identifier().hashCode() * 0x9E3779B97F4A7C15L)
                ^ (day * 0xBF58476D1CE4E5B9L)
                ^ ((long) season.visualSeason().ordinal()
                * 0x94D049BB133111EBL);

        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;

        double roll = (value >>> 11) * 0x1.0p-53;

        return roll < clampedPercent / 100.0;
    }

    private static boolean isDrySeason(VisualSeason visualSeason) {
        return visualSeason == VisualSeason.SPRING
                || visualSeason == VisualSeason.SUMMER;
    }

    private static Biome.Precipitation rainOrSnow(
            Level level,
            BlockPos pos,
            Season season,
            boolean isDay
    ) {
        return isWarmEnough(level, pos, season, isDay)
                ? Biome.Precipitation.RAIN
                : Biome.Precipitation.SNOW;
    }

    /**
     * Temperatures are expressed in Celsius. Below 0°C means snow;
     * 0°C and above means rain.
     */
    private static boolean isWarmEnough(
            Level level,
            BlockPos pos,
            Season season,
            boolean isDay
    ) {
        SeasonalBiomeData data = lookup(level, pos);
        if (data == null) {
            return false;
        }

        double temperature =
                data.getTemperature(season.temperatureSeason(), isDay);

        return temperature >= WeatherConstants.SNOW_TEMPERATURE_THRESHOLD_C;
    }

    /**
     * Biomes listed in INHERITS_SURROUNDINGS use their neighbours' blended
     * temperature profile.
     */
    private static SeasonalBiomeData lookup(Level level, BlockPos pos) {
        Identifier biomeId = getBiomeId(level, pos);
        if (biomeId == null) {
            return null;
        }

        if (BiomeTemperatureTable.INHERITS_SURROUNDINGS.contains(biomeId)) {
            return LocalTemperatureService.getBlendedProfile(level, pos);
        }

        return BiomeTemperatureTable.get(biomeId);
    }

    private static Identifier getBiomeId(Level level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);

        return biomeHolder.unwrapKey()
                .map(ResourceKey::identifier)
                .orElse(null);
    }
}
