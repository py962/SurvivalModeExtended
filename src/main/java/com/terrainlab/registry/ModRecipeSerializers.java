package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.recipe.RangefinderCompassRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.terrainlab.recipe.SuspiciousStewFlowerRecipe;
import com.terrainlab.recipe.CelestialConduitCraftRecipe;
import com.terrainlab.recipe.CelestialAmuletCraftRecipe;
import com.terrainlab.recipe.CelestialForgeRecipe;
import com.terrainlab.recipe.CelestialPairShapedRecipe;

/**
 * Custom recipe serializers. A recipe JSON's "type" resolves against this registry.
 */
public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, TerrainLab.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<RangefinderCompassRecipe>> RANGEFINDER_COMPASS =
            RECIPE_SERIALIZERS.register("rangefinder_compass",
                    () -> new RecipeSerializer<>(RangefinderCompassRecipe.MAP_CODEC, RangefinderCompassRecipe.STREAM_CODEC));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SuspiciousStewFlowerRecipe>> SUSPICIOUS_STEW_FLOWER =
            RECIPE_SERIALIZERS.register("suspicious_stew_flower",
                    () -> new RecipeSerializer<>(SuspiciousStewFlowerRecipe.MAP_CODEC, SuspiciousStewFlowerRecipe.STREAM_CODEC));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CelestialConduitCraftRecipe>> CELESTIAL_CONDUIT_CRAFT =
            RECIPE_SERIALIZERS.register("celestial_conduit_craft",
                    () -> new RecipeSerializer<>(CelestialConduitCraftRecipe.MAP_CODEC, CelestialConduitCraftRecipe.STREAM_CODEC));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CelestialAmuletCraftRecipe>> CELESTIAL_AMULET_CRAFT =
            RECIPE_SERIALIZERS.register("celestial_amulet_craft",
                    () -> new RecipeSerializer<>(CelestialAmuletCraftRecipe.MAP_CODEC, CelestialAmuletCraftRecipe.STREAM_CODEC));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CelestialForgeRecipe>> CELESTIAL_FORGE =
            RECIPE_SERIALIZERS.register("celestial_forge", () -> CelestialForgeRecipe.Serializer.INSTANCE);

    // Amulet and conduit recipes: shaped, in the recipe book, carrying the pearl's pair id.
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CelestialPairShapedRecipe>> CELESTIAL_PAIR_SHAPED =
            RECIPE_SERIALIZERS.register("celestial_pair_shaped", () -> CelestialPairShapedRecipe.SERIALIZER);

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
