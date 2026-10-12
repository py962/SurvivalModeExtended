package com.terrainlab.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Set;

/**
 * Biome groupings for the client ambience ({@code AmbienceClient}): continuous biome beds and
 * occasional wildlife calls.
 */
public class ModBiomeSets {

    private static final String MODID = "terrainlab";

    private static ResourceKey<Biome> custom(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MODID, path));
    }

    // Custom biome keys; paths must match the mod's biome definitions.
    public static final ResourceKey<Biome> MARSH = custom("marsh");
    public static final ResourceKey<Biome> FROZEN_MARSH = custom("frozen_marsh"); // unused: geese are a temperate wetland call

    // Forest birdsong bed by day, plus the occasional extra birdsong.
    public static final Set<ResourceKey<Biome>> ALL_FOREST = Set.of(
            Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST,
            Biomes.CHERRY_GROVE, Biomes.FLOWER_FOREST);

    public static final Set<ResourceKey<Biome>> JUNGLE = Set.of(
            Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE);

    // Swamp night bed. Marsh is left out (only its geese play there); Frozen Marsh is left quiet.
    public static final Set<ResourceKey<Biome>> WETLANDS = Set.of(
            Biomes.SWAMP, Biomes.MANGROVE_SWAMP);

    // Swamp and Marsh, but not Mangrove Swamp.
    public static final Set<ResourceKey<Biome>> GOOSE_BIOMES = Set.of(
            Biomes.SWAMP, MARSH);

    // Distant wolves at night.
    public static final Set<ResourceKey<Biome>> TAIGA_WILDLIFE = Set.of(
            Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.GROVE); // deliberately NOT Snowy Peaks

    // Distant owl calls: the same biomes owls spawn in.
    public static final Set<ResourceKey<Biome>> OWL_BIOMES = Set.of(
            Biomes.TAIGA, Biomes.SNOWY_TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.GROVE, Biomes.WINDSWEPT_FOREST,
            Biomes.OLD_GROWTH_BIRCH_FOREST);

    public static final Set<ResourceKey<Biome>> VULTURE_BIOMES = Set.of(
            Biomes.DESERT, Biomes.BADLANDS, Biomes.SAVANNA);

    // Night-only call, independent of the day-only vulture call.
    public static final Set<ResourceKey<Biome>> ELEPHANT_BIOMES = Set.of(
            Biomes.SAVANNA);

    /** True if the given biome holder's key is in the given set. */
    public static boolean isIn(Holder<Biome> biomeHolder, Set<ResourceKey<Biome>> set) {
        return biomeHolder.unwrapKey().map(set::contains).orElse(false);
    }
}
