package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * ResourceKeys for TerrainLab's data-driven enchantments (data/terrainlab/enchantment/*.json),
 * plus a helper to resolve them to live holders.
 */
public final class ModEnchantments {

    private ModEnchantments() {}

    public static final ResourceKey<Enchantment> OVERSTACKING = key("overstacking");
    public static final ResourceKey<Enchantment> AGILITY = key("agility");
    public static final ResourceKey<Enchantment> REACH = key("reach");
    public static final ResourceKey<Enchantment> RECALL = key("recall");
    public static final ResourceKey<Enchantment> CLEAVE = key("cleave");

    private static ResourceKey<Enchantment> key(String path) {
        return ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(TerrainLab.MODID, path));
    }

    public static Holder<Enchantment> holder(RegistryAccess registryAccess, ResourceKey<Enchantment> key) {
        return registryAccess.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }
}
