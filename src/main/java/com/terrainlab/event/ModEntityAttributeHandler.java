package com.terrainlab.event;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

/** Adjusts vanilla entity attributes: Snow Golem max health. */
@EventBusSubscriber(modid = com.terrainlab.TerrainLab.MODID)
public class ModEntityAttributeHandler {

    // 4 hearts (vanilla: 2).
    private static final double SNOW_GOLEM_MAX_HEALTH = 8.0;

    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        event.add(EntityType.SNOW_GOLEM, Attributes.MAX_HEALTH, SNOW_GOLEM_MAX_HEALTH);
    }
}
