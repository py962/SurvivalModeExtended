package com.terrainlab.client.debug;

import com.terrainlab.TerrainLab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(value = Dist.CLIENT)
public class SpectatorDebugTrace {

    // Gates the once-per-second spectator camera trace (the log call itself is commented out).
    private static final boolean DEBUG_LOGGING = false;

    private static int tickCounter = 0;

    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        if (!DEBUG_LOGGING) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!player.isSpectator()) return;

        tickCounter++;
        if (tickCounter % 20 != 0) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Entity cameraEntity = mc.getCameraEntity();

        // TerrainLab.LOGGER.info(
                // "[SPECTATOR_TRACE] player.isSpectator={}, player.noPhysics={}, "
                        // + "cameraEntity={}, cameraEntity==player={}, cameraEntity.isSpectator={}, "
                        // + "camPos={}, blockAtCam={}, cameraInitialized={}",
                // player.isSpectator(),
                // player.noPhysics,
                // cameraEntity,
                // cameraEntity == player,
                // cameraEntity != null && cameraEntity.isSpectator(),
                // camera.blockPosition(),
                // camera.getBlockAtCamera(),
                // camera.isInitialized());
    }
}
