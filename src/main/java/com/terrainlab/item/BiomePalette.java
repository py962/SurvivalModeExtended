package com.terrainlab.item;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.MapColor;

/** Classifies biomes into the atlas colour categories; unknown biomes render as plains. */
public final class BiomePalette {

    private BiomePalette() {}

    public static MapColor classify(Holder<Biome> biome) {
        Identifier id = biomeId(biome);

        if (id == null) {
            return AtlasColors.PLAINS;
        }

        return switch (id.toString()) {
            // Arid
            case "minecraft:desert",
                 "minecraft:beach",
                 "minecraft:badlands",
                 "minecraft:eroded_badlands" ->
                    AtlasColors.ARID;

            // Arid woodland
            case "minecraft:savanna",
                 "minecraft:savanna_plateau",
                 "minecraft:windswept_savanna",
                 "minecraft:wooded_badlands" ->
                    AtlasColors.ARID_WOODLAND;

            // Tropical woodland
            case "minecraft:jungle",
                 "minecraft:sparse_jungle",
                 "minecraft:bamboo_jungle"
                ->
                    AtlasColors.TROPICAL_WOODLAND;

            // Temperate
            case "minecraft:plains",
                 "minecraft:sunflower_plains",
                 "minecraft:meadow",
                 "minecraft:windswept_hills",
                 "minecraft:windswept_gravelly_hills",
                 "minecraft:mushroom_fields"->
                    AtlasColors.PLAINS;

            // Temperate woodland
            case "minecraft:forest",
                 "minecraft:flower_forest",
                 "minecraft:birch_forest",
                 "minecraft:old_growth_birch_forest",
                 "minecraft:dark_forest",
                 "minecraft:pale_garden",
                 "minecraft:cherry_grove",
                 "minecraft:windswept_forest" ->
                    AtlasColors.TEMPERATE_WOODLAND;

            // Wetlands
            case "terrainlab:marsh",
                 "minecraft:swamp",
                 "minecraft:mangrove_swamp" ->
                    AtlasColors.WETLANDS;

            // Boreal woodland
            case "minecraft:snowy_taiga",
                 "minecraft:grove",
                 "minecraft:taiga",
                 "minecraft:old_growth_pine_taiga",
                 "minecraft:old_growth_spruce_taiga" ->
                    AtlasColors.BOREAL_WOODLAND;

            // Polar
            case "terrainlab:frozen_marsh",
                 "minecraft:snowy_plains",
                 "minecraft:snowy_slopes",
                 "minecraft:ice_spikes",
                 "minecraft:frozen_river",
                 "minecraft:snowy_beach",
                 "minecraft:deep_frozen_ocean",
                 "minecraft:frozen_peaks",
                 "minecraft:jagged_peaks",
                 "minecraft:frozen_ocean" ->
                    AtlasColors.SNOWY;

            // Mountain (normally caught first by AtlasMapGenerator's elevation check)
            case
                    "minecraft:stony_shore",
                    "minecraft:stony_peaks" ->
                    AtlasColors.STONY;

            // Water (normally caught first by AtlasMapGenerator's sea-level check)
            case "minecraft:river",
                 "terrainlab:inland_river",
                 "minecraft:ocean",
                 "minecraft:deep_ocean",
                 "minecraft:cold_ocean",
                 "minecraft:deep_cold_ocean",
                 "minecraft:lukewarm_ocean",
                 "minecraft:deep_lukewarm_ocean",
                 "minecraft:warm_ocean"
                 ->
                    AtlasColors.OCEAN;

            default ->
                    AtlasColors.PLAINS;
        };
    }

    private static Identifier biomeId(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(key -> key.identifier())
                .orElse(null);
    }
}
