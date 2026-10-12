package com.terrainlab.client.hud;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.minecraft.resources.Identifier;
import com.terrainlab.client.HeatstrokeVignetteLayer;

@EventBusSubscriber(value = Dist.CLIENT)
public class HeatstrokeVignetteRegistration {

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath("terrainlab", "heatstroke_vignette"),
                new HeatstrokeVignetteLayer());
    }
}
