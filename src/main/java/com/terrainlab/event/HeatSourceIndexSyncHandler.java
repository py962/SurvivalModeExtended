package com.terrainlab.event;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.temperature.HeatSourceIndex;
import com.terrainlab.temperature.HeatSourceSyncService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Sends every indexed heat source in the player's level on login. Unused:
 * superseded by per-chunk sync in HeatSourceIndexHandler and not registered.
 */
public class HeatSourceIndexSyncHandler {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        HeatSourceIndex index = serverLevel.getData(ModAttachments.HEAT_SOURCE_INDEX.get());
        HeatSourceSyncService.sendAllTo(player, index);
    }
}
