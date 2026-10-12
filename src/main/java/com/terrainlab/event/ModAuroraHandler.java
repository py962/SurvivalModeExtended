package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.network.SyncAuroraPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side aurora trigger: the aurora is active in the Overworld at night while any
 * sky-exposed player stands where the current-season biome temperature is below -10.
 * State changes are synced to clients via {@link SyncAuroraPayload}.
 */
public class ModAuroraHandler {

    private static final float AURORA_TEMP_THRESHOLD = -10.0f;
    private static final int CHECK_INTERVAL = 20; // ticks

    private static final Map<ResourceKey<Level>, Boolean> activeStateByDimension = new ConcurrentHashMap<>();

    /** Whether the aurora is currently active in the given dimension (server-side state). */
    public static boolean isAuroraActive(ResourceKey<Level> dimension) {
        return activeStateByDimension.getOrDefault(dimension, false);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (!level.dimensionType().hasSkyLight()) {
            // TerrainLab.LOGGER.info("[AURORA] dimension {} has no sky light, skipping", level.dimension());
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime % CHECK_INTERVAL != 0) {
            return;
        }

        boolean night = isNight(level);
        boolean anyPlayerCold = anyPlayerMeetsAuroraConditions(level);
        boolean shouldBeActive = night && anyPlayerCold;

        ResourceKey<Level> dimensionKey = level.dimension();
        boolean wasActive = activeStateByDimension.getOrDefault(dimensionKey, false);

        // TerrainLab.LOGGER.info(
        //         "[AURORA] check: night={}, anyPlayerCold={}, shouldBeActive={}, wasActive={}",
        //         night, anyPlayerCold, shouldBeActive, wasActive);

        if (shouldBeActive == wasActive) {
            return;
        }

        activeStateByDimension.put(dimensionKey, shouldBeActive);

        if (shouldBeActive) {
            startAurora(level, gameTime);
        } else {
            endAurora(level);
        }
    }

    private static boolean isNight(ServerLevel level) {
        // Judged by solar time so night length follows the season.
        long dayTime = com.terrainlab.season.SolarTime.solarTimeOfDay(level);
        return dayTime >= 13000 && dayTime < 23000;
    }

    private static boolean anyPlayerMeetsAuroraConditions(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();

            boolean canSeeSky = level.canSeeSky(pos);
            if (!canSeeSky) {
                // TerrainLab.LOGGER.info("[AURORA] player {} cannot see sky at {}, skipping", player.getUUID(), pos);
                continue;
            }

            float biomeTemp = getBiomeTemperature(level, pos);
            boolean coldEnough = biomeTemp < AURORA_TEMP_THRESHOLD;

            // TerrainLab.LOGGER.info(
            //         "[AURORA] player {} at {}: biomeTemp={}, threshold={}, coldEnough={}",
            //         player.getUUID(), pos, biomeTemp, AURORA_TEMP_THRESHOLD, coldEnough);

            if (coldEnough) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the blended biome temperature at {@code pos} for the world's current
     * temperature season and solar day/night phase.
     */
    private static float getBiomeTemperature(ServerLevel level, BlockPos pos) {
        var blended = com.terrainlab.temperature.BiomeTemperatureBlender.blend(
                level, pos, com.terrainlab.temperature.ColumnTemperatureProfile.DEFAULT_BLEND_RADIUS);

        if (blended == null) {
            blended = com.terrainlab.temperature.BiomeTemperatureBlender.climateNoiseFallback(level, pos);
            if (blended != null) {

            }
        }
        if (blended == null) {

            return 15.0f;
        }

        com.terrainlab.data.SeasonSavedData seasonData = com.terrainlab.data.SeasonSavedData.get(level);
        seasonData.generateOffsetIfAbsent(level.getSeed());
        long seasonalOffset = seasonData.getSeasonalOffset();

        var season = com.terrainlab.season.SeasonManager.getCurrentTemperatureSeason(level.getGameTime(), seasonalOffset);
        boolean isDay = com.terrainlab.season.SolarTime.isSolarDay(level);

        return (float) blended.getTemperature(season, isDay);
    }

    private static void startAurora(ServerLevel level, long gameTime) {
        // TerrainLab.LOGGER.info("[AURORA] STARTING in {} at tick {} (night + biome temp < {})",
        //         level.dimension(), gameTime, AURORA_TEMP_THRESHOLD);

        syncToDimension(level, true);
    }

    private static void endAurora(ServerLevel level) {
        // TerrainLab.LOGGER.info("[AURORA] ENDING in {}", level.dimension());

        syncToDimension(level, false);
    }

    private static void syncToDimension(ServerLevel level, boolean active) {
        SyncAuroraPayload payload = new SyncAuroraPayload(active);
        int playerCount = 0;
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
            playerCount++;
        }
        // TerrainLab.LOGGER.info("[AURORA] syncToDimension: sent active={} to {} player(s)", active, playerCount);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncToPlayer(player);
        // TerrainLab.LOGGER.info("[AURORA] onPlayerLoggedIn: synced current state to {}", player.getUUID());
    }

    /** Resends the aurora state for the player's new dimension. */
    @SubscribeEvent
    public static void onChangedDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    private static void syncToPlayer(ServerPlayer player) {
        boolean active = activeStateByDimension.getOrDefault(player.level().dimension(), false);
        PacketDistributor.sendToPlayer(player, new SyncAuroraPayload(active));
    }
}
