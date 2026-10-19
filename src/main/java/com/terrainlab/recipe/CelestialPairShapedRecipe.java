package com.terrainlab.recipe;

import com.mojang.serialization.MapCodec;
import com.terrainlab.item.CelestialPearlItem;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A normal shaped recipe (shown in the recipe book) that also copies the Celestial pair id and
 * variant from the pearl in the grid onto the result. Used for the amulet and conduit recipes.
 */
public class CelestialPairShapedRecipe extends ShapedRecipe {

    public CelestialPairShapedRecipe(ShapedRecipe base) {
        super(base.group(), base.category(), base.pattern, base.assemble(CraftingInput.EMPTY, null), base.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        if (result.isEmpty()) {
            return result;
        }
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof CelestialPearlItem) {
                var pairId = stack.get(ModDataComponents.CELESTIAL_PAIR_ID.get());
                if (pairId != null) result.set(ModDataComponents.CELESTIAL_PAIR_ID.get(), pairId);
                var variant = stack.get(ModDataComponents.CELESTIAL_VARIANT.get());
                if (variant != null) result.set(ModDataComponents.CELESTIAL_VARIANT.get(), variant);
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<CelestialPairShapedRecipe> getSerializer() {
        return ModRecipeSerializers.CELESTIAL_PAIR_SHAPED.get();
    }

    public static class Serializer implements RecipeSerializer<CelestialPairShapedRecipe> {
        private static final MapCodec<CelestialPairShapedRecipe> CODEC =
                ShapedRecipe.Serializer.CODEC.xmap(CelestialPairShapedRecipe::new, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, CelestialPairShapedRecipe> STREAM_CODEC =
                ShapedRecipe.Serializer.STREAM_CODEC.map(CelestialPairShapedRecipe::new, recipe -> recipe);

        @Override
        public MapCodec<CelestialPairShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CelestialPairShapedRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
