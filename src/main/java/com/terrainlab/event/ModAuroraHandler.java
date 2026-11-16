package com.terrainlab.event;

import com.terrainlab.network.SyncAuroraPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side aurora. At night it hangs over every Overworld biome whose current-season temperature
 * is below -10 (altitude doesn't count). Each player sees it only while standing in such a biome;
 * their state is synced via {@link SyncAuroraPayload} whenever it changes.
 */
public class ModAuroraHandler {

    private static final float AURORA_TEMP_THRESHOLD = -10.0f;
    private static final int CHECK_INTERVAL = 20; // ticks

    private static final Map<UUID, Boolean> visibleByPlayer = new ConcurrentHashMap<>();

    /** Whether an aurora is overhead at {@code pos}: Overworld, night and a cold enough biome. */
    public static boolean isAuroraOver(ServerLevel level, BlockPos pos) {
        return level.dimension() == Level.OVERWORLD && isNight(level) && isColdEnough(level, pos);
    }

    /** Whether the biome at {@code pos} is cold enough for an aurora right now (no altitude chill). */
    public static boolean isColdEnough(ServerLevel level, BlockPos pos) {
        return getBiomeTemperature(level, pos) < AURORA_TEMP_THRESHOLD;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }
        boolean night = isNight(level);
        for (ServerPlayer player : level.players()) {
            boolean visible = night && isColdEnough(level, player.blockPosition());
            Boolean previous = visibleByPlayer.put(player.getUUID(), visible);
            if (previous == null || previous != visible) {
                PacketDistributor.sendToPlayer(player, new SyncAuroraPayload(visible));
            }
        }
    }

    private static boolean isNight(ServerLevel level) {
        // Judged by solar time so night length follows the season.
        long dayTime = com.terrainlab.season.SolarTime.solarTimeOfDay(level);
        return dayTime >= 13000 && dayTime < 23000;
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

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        visibleByPlayer.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncToPlayer(player);
        }
    }

    /** Sends the player's current state unconditionally (the client may have reset its own). */
    private static void syncToPlayer(ServerPlayer player) {
        boolean visible = player.level() instanceof ServerLevel level && isAuroraOver(level, player.blockPosition());
        visibleByPlayer.put(player.getUUID(), visible);
        PacketDistributor.sendToPlayer(player, new SyncAuroraPayload(visible));
    }
}
