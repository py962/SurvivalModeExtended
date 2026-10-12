package com.terrainlab.client.hud;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Replaces the vanilla armor bar layer with {@link EnergyBarLayer}. */
@EventBusSubscriber(value = Dist.CLIENT)
public class EnergyBarRegistration {

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.ARMOR_LEVEL, new EnergyBarLayer());
    }
}
