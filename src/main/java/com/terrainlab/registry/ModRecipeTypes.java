package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.recipe.CelestialForgeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom recipe types. The Celestial Forge needs its own type because it is a
 * 9-slot container rather than a crafting grid.
 */
public class ModRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, TerrainLab.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<CelestialForgeRecipe>> CELESTIAL_FORGE =
            RECIPE_TYPES.register("celestial_forge", () ->
                    RecipeType.simple(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "celestial_forge")));

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
    }
}
