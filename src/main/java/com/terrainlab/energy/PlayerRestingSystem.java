package com.terrainlab.energy;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

import static com.terrainlab.energy.PlayerEnergyConstants.*;

/**
 * Converts hunger into health/energy when a player wakes, in proportion to the simulated night slept.
 *
 * <p>The day time is snapshotted on {@link CanPlayerSleepEvent} (which also fires for rejected
 * attempts; those snapshots are simply overwritten). On wake, elapsed day time is divided by
 * FULL_NIGHT_WINDOW_TICKS (capped at 1.0) and floor(fraction * MAX_HUNGER_CONVERSIONS_PER_NIGHT)
 * points are converted. Simulated time is used because the sleep skip compresses real ticks.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class PlayerRestingSystem {

    private PlayerRestingSystem() {
    }

    @SubscribeEvent
    public static void onSleepStart(CanPlayerSleepEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        long dayTime = player.level().getOverworldClockTime();
        player.setData(ModAttachments.PLAYER_SLEEP_START_DAYTIME.get(), dayTime);

        // TerrainLab.LOGGER.info("[REST] sleep started: dayTime snapshot={}", dayTime);
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        long startDayTime = player.getData(ModAttachments.PLAYER_SLEEP_START_DAYTIME.get());
        player.setData(ModAttachments.PLAYER_SLEEP_START_DAYTIME.get(), -1L);

        if (startDayTime < 0) {
            // TerrainLab.LOGGER.info("[REST] wake with no sleep-start snapshot recorded, aborting (no reward)");
            return;
        }

        long currentDayTime = player.level().getOverworldClockTime();
        long elapsed = currentDayTime - startDayTime;

        if (elapsed <= 0) {
            // TerrainLab.LOGGER.info("[REST] wake: non-positive elapsed simulated time ({}), aborting", elapsed);
            return;
        }

        double fraction = Math.min(1.0, elapsed / (double) FULL_NIGHT_WINDOW_TICKS);
        int conversions = (int) Math.floor(fraction * MAX_HUNGER_CONVERSIONS_PER_NIGHT);

        // TerrainLab.LOGGER.info(
                // "[REST] wake: elapsed={} simulated ticks, fraction={}, conversions={}",
                // elapsed, fraction, conversions);

        com.terrainlab.player.StatFlash.Snapshot before = com.terrainlab.player.StatFlash.capture(player);
        for (int i = 0; i < conversions; i++) {
            boolean converted = tryConvertOnePoint(player);
            if (!converted) {
                // TerrainLab.LOGGER.info("[REST] conversion {} of {} stopped early (hunger too low)", i + 1, conversions);
                break;
            }
        }
        // Golden flash on whatever the night restored (health and/or energy; hunger only drops).
        com.terrainlab.player.StatFlash.sendGains(player, before);
    }

    /**
     * Converts one hunger point, healing first and otherwise restoring energy up to REST_CAP.
     *
     * @return false if hunger is too low for either branch
     */
    private static boolean tryConvertOnePoint(ServerPlayer player) {
        int hunger = player.getFoodData().getFoodLevel();
        boolean healthNotFull = player.getHealth() < player.getMaxHealth();
        float energy = player.getData(ModAttachments.PLAYER_ENERGY.get());
        boolean energyNotCapped = energy < PlayerEnergyConstants.REST_CAP;

        if (healthNotFull && hunger >= MIN_HUNGER_FOR_HEALTH_REST) {
            player.heal(HEALTH_PER_HUNGER_POINT);
            player.getFoodData().setFoodLevel(hunger - 1);
            return true;
        } else if (energyNotCapped && hunger >= MIN_HUNGER_FOR_ENERGY_REST) {
            float newEnergy = Math.min(PlayerEnergyConstants.REST_CAP, energy + ENERGY_PER_HUNGER_POINT);
            player.setData(ModAttachments.PLAYER_ENERGY.get(), newEnergy);
            player.getFoodData().setFoodLevel(hunger - 1);
            return true;
        }
        return false;
    }
}
