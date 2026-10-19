package com.terrainlab.client;

import com.terrainlab.network.DeathConduitRespawnPacket;
import com.terrainlab.network.DeathStartPickingPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Adds the Celestial Death respawn-choice buttons to the vanilla DeathScreen, each only when
 * the server reported that option as available for this death ({@link ClientDeathOptionsState},
 * fed by DeathOptionsPacket). Nothing is added in hardcore worlds, where the Death conduit
 * instead prevents the death outright. The server re-validates every choice.
 */
public class CelestialDeathScreenHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen screen)) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getLevelData().isHardcore()) {
            return;
        }
        boolean conduitAvailable = ClientDeathOptionsState.conduitAvailable();
        boolean pickAvailable = ClientDeathOptionsState.pickAvailable();
        if (!conduitAvailable && !pickAvailable) {
            return;
        }

        int width = screen.width;
        int centerX = width / 2;
        int y = screen.height / 4 + 120;

        if (conduitAvailable) {
            event.addListener(Button.builder(
                            Component.translatable("gui.terrainlab.death.respawn_at_conduit"),
                            button -> {
                                ClientPacketDistributor.sendToServer(new DeathConduitRespawnPacket());
                                performVanillaRespawn();
                            })
                    .bounds(centerX - 100, y, 200, 20)
                    .build());
            y += 24;
        }

        if (pickAvailable) {
            event.addListener(Button.builder(
                            Component.translatable("gui.terrainlab.death.choose_respawn_location"),
                            button -> {
                                ClientPacketDistributor.sendToServer(new DeathStartPickingPacket());
                                performVanillaRespawn();
                            })
                    .bounds(centerX - 100, y, 200, 20)
                    .build());
        }
    }

    /**
     * DeathOptionsPacket handler. The packet is normally processed before the DeathScreen opens;
     * if a DeathScreen is already showing, it is re-initialised so the buttons appear.
     */
    public static void onOptionsReceived(boolean conduit, boolean pick) {
        ClientDeathOptionsState.set(conduit, pick);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof DeathScreen screen) {
            screen.init(screen.width, screen.height);
        }
    }

    /** Sends the same PERFORM_RESPAWN command as vanilla's Respawn button. */
    private static void performVanillaRespawn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.send(new ServerboundClientCommandPacket(
                    ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        }
    }
}
