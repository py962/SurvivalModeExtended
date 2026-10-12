package com.terrainlab.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Equipment asset keys for mod-rendered equipment. */
public class ModEquipmentAssets {

    public static final ResourceKey<EquipmentAsset> HOWDAH = createId("howdah");

    private static ResourceKey<EquipmentAsset> createId(String name) {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("terrainlab", name));
    }
}
