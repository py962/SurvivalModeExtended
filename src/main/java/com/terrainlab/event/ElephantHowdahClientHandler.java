package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.network.HowdahSleepRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Client-side howdah sleep trigger. Vanilla picking never hits the player's own mount, so an
 * empty-hand right-click on a block while riding an elephant and looking steeply down is
 * treated as a click on the elephant and sent as a {@link HowdahSleepRequestPacket}.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public class ElephantHowdahClientHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return; // otherwise sent again for the off hand
        if (!ElephantHowdahServerHandler.isHowdahClick(player)) return;

        // Cancel so the block under the crosshair doesn't also handle this click; the server
        // cancels its copy of the event in ElephantHowdahServerHandler.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        // TerrainLab.LOGGER.info("[HOWDAH_CLIENT] sending HowdahSleepRequestPacket");
        mc.getConnection().send(new HowdahSleepRequestPacket());
    }
}
