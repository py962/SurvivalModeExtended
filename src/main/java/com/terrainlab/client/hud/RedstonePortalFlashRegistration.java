package com.terrainlab.client.hud;

import com.terrainlab.client.RedstonePortalFlashLayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public class RedstonePortalFlashRegistration {

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // Below the HUD, like vanilla's camera overlays, so hotbar and chat stay readable.
        event.registerBelowAll(
                Identifier.fromNamespaceAndPath("terrainlab", "redstone_portal_flash"),
                new RedstonePortalFlashLayer());
    }
}
