package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.item.properties.SeasonAngle;
import com.terrainlab.client.item.properties.ThermometerReading;
import com.terrainlab.client.item.properties.RangefinderDistance;
import com.terrainlab.client.item.properties.BubbleGaugeReading;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;

/**
 * Registers the mod's range-select item model properties. Client-only: must never be
 * referenced on a dedicated server.
 */
public class TerrainLabItemModelProperties {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(TerrainLabItemModelProperties::registerRangeSelectProperties);
    }

    private static void registerRangeSelectProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "season_angle"),
                SeasonAngle.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "thermometer_reading"),
                ThermometerReading.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "bubble_gauge_reading"),
                BubbleGaugeReading.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "rangefinder_distance"),
                RangefinderDistance.MAP_CODEC
        );
    }
}
