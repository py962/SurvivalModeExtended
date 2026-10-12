package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.ElephantEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Server half of the howdah sleep click: the client still sends the block interaction, so the
 * server cancels it to stop the block under the elephant reacting (doors, chests, buttons).
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class ElephantHowdahServerHandler {

    // Degrees below horizontal (getXRot: 0 = level, 90 = straight down) that count as looking at the elephant.
    public static final float SLEEP_PITCH_THRESHOLD = 70.0F;

    private ElephantHowdahServerHandler() {
    }

    /** Empty main hand, riding an elephant and looking steeply down. */
    public static boolean isHowdahClick(Player player) {
        return player.getVehicle() instanceof ElephantEntity
                && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                && player.getXRot() >= SLEEP_PITCH_THRESHOLD;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (isHowdahClick(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
