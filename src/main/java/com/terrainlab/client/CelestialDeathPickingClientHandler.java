package com.terrainlab.client;

import com.terrainlab.network.DeathConfirmPickingPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Client half of death-amulet spectator location picking: while picking is active,
 * draws the confirm prompt as a HUD overlay (a Screen would steal mouse-look) and
 * consumes the attack key to send the confirm packet.
 */
public class CelestialDeathPickingClientHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ClientDeathPickingState.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.options.keyAttack.consumeClick()
                || mc.options.keyUse.consumeClick()) {
            ClientPacketDistributor.sendToServer(new DeathConfirmPickingPacket());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!ClientDeathPickingState.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        int seconds = ClientDeathPickingState.ticksRemaining() / 20;
        Component text = Component.translatable("gui.terrainlab.death.picking_prompt", seconds);
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 40;
        graphics.centeredText(mc.font, text, x, y, 0xFFFFFF);
    }
}
