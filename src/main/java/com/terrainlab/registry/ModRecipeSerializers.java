package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.recipe.RangefinderCompassRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.terrainlab.recipe.SuspiciousStewFlowerRecipe;
import com.terrainlab.recipe.CelestialConduitCraftRecipe;
import com.terrainlab.recipe.CelestialAmuletCraftRecipe;
import com.terrainlab.recipe.CelestialForgeRecipe;

/**
 * Custom recipe serializers. A recipe JSON's "type" resolves against this registry.
 */
public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, TerrainLab.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, CustomRecipe.Serializer<RangefinderCompassRecipe>> RANGEFINDER_COMPASS =
            RECIPE_SERIALIZERS.register("rangefinder_compass",
                    () -> new CustomRecipe.Serializer<>(RangefinderCompassRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, CustomRecipe.Serializer<SuspiciousStewFlowerRecipe>> SUSPICIOUS_STEW_FLOWER =
            RECIPE_SERIALIZERS.register("suspicious_stew_flower",
                    () -> new CustomRecipe.Serializer<>(SuspiciousStewFlowerRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, CustomRecipe.Serializer<CelestialConduitCraftRecipe>> CELESTIAL_CONDUIT_CRAFT =
            RECIPE_SERIALIZERS.register("celestial_conduit_craft",
                    () -> new CustomRecipe.Serializer<>(CelestialConduitCraftRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, CustomRecipe.Serializer<CelestialAmuletCraftRecipe>> CELESTIAL_AMULET_CRAFT =
            RECIPE_SERIALIZERS.register("celestial_amulet_craft",
                    () -> new CustomRecipe.Serializer<>(CelestialAmuletCraftRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, CelestialForgeRecipe.Serializer> CELESTIAL_FORGE =
            RECIPE_SERIALIZERS.register("celestial_forge", () -> CelestialForgeRecipe.Serializer.INSTANCE);

    // Amulet and conduit recipes: shaped, in the recipe book, carrying the pearl's pair id.
    public static final DeferredHolder<RecipeSerializer<?>, com.terrainlab.recipe.CelestialPairShapedRecipe.Serializer> CELESTIAL_PAIR_SHAPED =
            RECIPE_SERIALIZERS.register("celestial_pair_shaped", com.terrainlab.recipe.CelestialPairShapedRecipe.Serializer::new);

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
