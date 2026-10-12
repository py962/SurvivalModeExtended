package com.terrainlab.client.hud;

import com.terrainlab.TerrainLab;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Registers {@link TemperatureHudLayer} above all other GUI layers. */
@EventBusSubscriber(value = Dist.CLIENT)
public class TemperatureHudRegistration {

    @SubscribeEvent
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "temperature_hud"),
                new TemperatureHudLayer()
        );
    }
}
