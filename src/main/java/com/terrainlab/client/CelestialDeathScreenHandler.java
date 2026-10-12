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
 * Adds the two Celestial Death respawn-choice buttons to the vanilla DeathScreen.
 * The buttons are always shown; the server falls back to a normal respawn when the
 * player is not eligible.
 */
public class CelestialDeathScreenHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DeathScreen screen)) {
            return;
        }

        int width = screen.width;
        int centerX = width / 2;

        Button conduitButton = Button.builder(
                        Component.translatable("gui.terrainlab.death.respawn_at_conduit"),
                        button -> {
                            ClientPacketDistributor.sendToServer(new DeathConduitRespawnPacket());
                            performVanillaRespawn();
                        })
                .bounds(centerX - 100, screen.height / 4 + 120, 200, 20)
                .build();

        Button pickButton = Button.builder(
                        Component.translatable("gui.terrainlab.death.choose_respawn_location"),
                        button -> {
                            ClientPacketDistributor.sendToServer(new DeathStartPickingPacket());
                            performVanillaRespawn();
                        })
                .bounds(centerX - 100, screen.height / 4 + 144, 200, 20)
                .build();

        event.addListener(conduitButton);
        event.addListener(pickButton);
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
