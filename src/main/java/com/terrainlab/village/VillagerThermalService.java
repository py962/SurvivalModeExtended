package com.terrainlab.village;

import com.terrainlab.celestial.CelestialConduitRegistry;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.temperature.LocalTemperatureService;
import com.terrainlab.temperature.WaterImmersionEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Updates a villager's thermal state from ambient temperature plus wetness cooling, smoothed into a
 * felt temperature and compared against the cold/hot thresholds. Called from the staggered
 * per-villager read in VillagerBehaviorHandler.
 */
public final class VillagerThermalService {

    private VillagerThermalService() {
    }

    public static void update(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = villager.blockPosition();
        long offset = SeasonSavedData.get(level).getSeasonalOffset();
        var season = SeasonManager.getCurrentTemperatureSeason(level.getGameTime(), offset);
        // Day/night blend by the warped sun, matching players.
        double nightFraction = com.terrainlab.season.SolarTime.nightFraction(level);

        double ambientTemp = LocalTemperatureService.getAmbientTemperature(level, pos, season, nightFraction);

        boolean immersedNow = villager.isEyeInFluid(FluidTags.WATER);
        float wetness = villager.getData(ModAttachments.VILLAGER_WETNESS.get());
        wetness = immersedNow ? 1.0F : (float) WaterImmersionEffect.decay(wetness, VillageConstants.STAGGER_INTERVAL_TICKS);
        villager.setData(ModAttachments.VILLAGER_WETNESS.get(), wetness);

        double combinedTemp = ambientTemp + WaterImmersionEffect.coolingContribution(wetness);

        // Warming/cooling food from the village chest: a timed offset that decays linearly to 0.
        VillagerFoodTempOffset foodOffset = villager.getData(ModAttachments.VILLAGER_FOOD_TEMP_OFFSET.get());
        if (foodOffset.offset() != 0.0F) {
            long now = level.getGameTime();
            if (now >= foodOffset.endTick()) {
                villager.setData(ModAttachments.VILLAGER_FOOD_TEMP_OFFSET.get(), VillagerFoodTempOffset.NONE);
            } else {
                combinedTemp += foodOffset.currentAt(now);
            }
        }

        // A Shield conduit in range holds the villager at a comfortable temperature.
        if (CelestialConduitRegistry.get(level).hasActiveNearby(CelestialVariant.SHIELD, level.dimension(), pos)) {
            combinedTemp = VillageConstants.THERMAL_SHIELD_COMFORT_C;
        }

        // Exponential smoothing toward the current temperature, one step per staggered read.
        float felt = villager.getData(ModAttachments.VILLAGER_FELT_TEMP.get());
        if (Float.isNaN(felt)) {
            felt = (float) combinedTemp;
        } else {
            double alpha = 1.0 - Math.exp(-VillageConstants.STAGGER_INTERVAL_TICKS
                    / VillageConstants.THERMAL_FELT_TIME_CONSTANT_TICKS);
            felt += (float) ((combinedTemp - felt) * alpha);
        }
        villager.setData(ModAttachments.VILLAGER_FELT_TEMP.get(), felt);

        VillagerThermalState state;
        if (felt <= VillageConstants.THERMAL_COLD_THRESHOLD_C) {
            state = VillagerThermalState.COLD;
        } else if (felt >= VillageConstants.THERMAL_HOT_THRESHOLD_C) {
            state = VillagerThermalState.HOT;
        } else {
            state = VillagerThermalState.COMFORTABLE;
        }

        if (villager.getData(ModAttachments.VILLAGER_THERMAL_STATE.get()) != state) {
            villager.setData(ModAttachments.VILLAGER_THERMAL_STATE.get(), state);
        }
    }
}
