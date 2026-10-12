package com.terrainlab.event;

import com.terrainlab.item.AtlasGenerationQueue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Periodically scans player inventories for fresh, never-generated Atlases (from trades, loot,
 * creative, commands or pickups) and queues their generation, attaching a pre-generated map
 * when one is available.
 */
public class AtlasAcquisitionHandler {

    private static final int SCAN_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (player.tickCount % SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        for (
                int slot = 0;
                slot < serverPlayer.getInventory().getContainerSize();
                slot++
        ) {
            ItemStack stack =
                    serverPlayer.getInventory().getItem(slot);

            if (AtlasGenerationQueue.isUntouched(stack)) {
                AtlasGenerationQueue.ensureQueued(
                        level,
                        serverPlayer,
                        stack
                );
            }
        }
    }
}
