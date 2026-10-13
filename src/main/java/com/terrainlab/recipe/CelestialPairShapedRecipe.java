package com.terrainlab.recipe;

import com.mojang.serialization.MapCodec;
import com.terrainlab.item.CelestialPearlItem;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A normal shaped recipe (shown in the recipe book) that also copies the Celestial pair id and
 * variant from the pearl in the grid onto the result. Used for the amulet and conduit recipes.
 */
public class CelestialPairShapedRecipe extends ShapedRecipe {

    public static final MapCodec<CelestialPairShapedRecipe> MAP_CODEC =
            ShapedRecipe.MAP_CODEC.xmap(CelestialPairShapedRecipe::new, recipe -> recipe);
    public static final StreamCodec<RegistryFriendlyByteBuf, CelestialPairShapedRecipe> STREAM_CODEC =
            ShapedRecipe.STREAM_CODEC.map(CelestialPairShapedRecipe::new, recipe -> recipe);
    public static final RecipeSerializer<CelestialPairShapedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public CelestialPairShapedRecipe(ShapedRecipe base) {
        super(new Recipe.CommonInfo(base.showNotification()),
                new CraftingRecipe.CraftingBookInfo(base.category(), base.group()),
                base.pattern,
                ItemStackTemplate.fromNonEmptyStack(base.assemble(CraftingInput.EMPTY)));
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = super.assemble(input);
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
    @SuppressWarnings("unchecked")
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        // ShapedRecipe.getSerializer() is typed to ShapedRecipe; the cast is safe by erasure.
        return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>) ModRecipeSerializers.CELESTIAL_PAIR_SHAPED.get();
    }
}
