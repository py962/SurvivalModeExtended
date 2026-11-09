package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.network.SeasonOffsetPacket;
import com.terrainlab.season.Season;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Loads the world's year length when the overworld loads, and sends the world's seasonal offset and year
 * length to each player on login so client-side season code has the real values; generates the offset on
 * first use.
 */
public class ModSeasonSyncHandler {

    /** Reads the stored year length before anything (worldgen, plants, the solar clock) reads the season clock. */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        try {
            Season.setYearLengthDays(level.getDataStorage().computeIfAbsent(SeasonSavedData.TYPE).getYearLengthDays());
        } catch (RuntimeException e) {
            TerrainLab.LOGGER.error("Could not read the season length at level load; the next login applies it", e);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SeasonSavedData data = SeasonSavedData.get(player.level());
        Season.setYearLengthDays(data.getYearLengthDays());
        data.generateOffsetIfAbsent(player.level().getSeed());
        PacketDistributor.sendToPlayer(player, new SeasonOffsetPacket(data.getSeasonalOffset(), data.getYearLengthDays()));
    }
}
