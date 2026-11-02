package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.item.ShieldDefinitions;
import com.terrainlab.item.ShieldFactory;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * The vanilla shield is the mod's Oak Shield (renamed in en_us, crafted only from oak planks): it
 * gets the oak shield's durability, enchantability and blocking behaviour from ShieldDefinitions.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class VanillaShieldComponentsHandler {

    private VanillaShieldComponentsHandler() {
    }

    @SubscribeEvent
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modify(Items.SHIELD, patch -> ShieldFactory.applyToVanillaShield(patch, ShieldDefinitions.byId("oak_shield")));
    }
}
