package com.terrainlab.event;

import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.network.SeasonOffsetPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the world's seasonal offset to each player on login so client-side season code
 * has the real value; generates the offset on first use.
 */
public class ModSeasonSyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        SeasonSavedData data = SeasonSavedData.get(player.level());
        data.generateOffsetIfAbsent(player.level().getSeed());
        PacketDistributor.sendToPlayer(player, new SeasonOffsetPacket(data.getSeasonalOffset()));
    }
}
