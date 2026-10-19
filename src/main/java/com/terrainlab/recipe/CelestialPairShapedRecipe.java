
package com.terrainlab.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrainlab.item.CelestialPearlItem;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.registry.ModRecipeSerializers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

public class CelestialPairShapedRecipe extends ShapedRecipe {

    private final ItemStackTemplate outputTemplate;

    public static final MapCodec<CelestialPairShapedRecipe> MAP_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Recipe.CommonInfo.MAP_CODEC
                            .forGetter(recipe ->
                                    new Recipe.CommonInfo(recipe.showNotification())),

                    CraftingRecipe.CraftingBookInfo.MAP_CODEC
                            .forGetter(recipe ->
                                    new CraftingRecipe.CraftingBookInfo(
                                            recipe.category(),
                                            recipe.group())),

                    ShapedRecipePattern.MAP_CODEC
                            .forGetter(recipe -> recipe.pattern),

                    ItemStackTemplate.CODEC
                            .fieldOf("result")
                            .forGetter(recipe -> recipe.outputTemplate)   ).apply(instance, CelestialPairShapedRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CelestialPairShapedRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Recipe.CommonInfo.STREAM_CODEC,
                    recipe -> new Recipe.CommonInfo(recipe.showNotification()),

                    CraftingRecipe.CraftingBookInfo.STREAM_CODEC,
                    recipe -> new CraftingRecipe.CraftingBookInfo(
                            recipe.category(),
                            recipe.group()),

                    ShapedRecipePattern.STREAM_CODEC,
                    recipe -> recipe.pattern,

                    ItemStackTemplate.STREAM_CODEC,
                    recipe -> recipe.outputTemplate,

                    CelestialPairShapedRecipe::new
            );

    public static final RecipeSerializer<CelestialPairShapedRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    public CelestialPairShapedRecipe(
            Recipe.CommonInfo commonInfo,
            CraftingRecipe.CraftingBookInfo bookInfo,
            ShapedRecipePattern pattern,
            ItemStackTemplate result
    ) {
        super(commonInfo, bookInfo, pattern, result);
        this.outputTemplate = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = super.assemble(input);

        if (result.isEmpty()) {
            return result;
        }

        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof CelestialPearlItem) {
                var pairId = stack.get(
                        ModDataComponents.CELESTIAL_PAIR_ID.get());

                if (pairId != null) {
                    result.set(
                            ModDataComponents.CELESTIAL_PAIR_ID.get(),
                            pairId);
                }

                var variant = stack.get(
                        ModDataComponents.CELESTIAL_VARIANT.get());

                if (variant != null) {
                    result.set(
                            ModDataComponents.CELESTIAL_VARIANT.get(),
                            variant);
                }

                break;
            }
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<ShapedRecipe> getSerializer() {
        return (RecipeSerializer<ShapedRecipe>) (RecipeSerializer<?>)
                ModRecipeSerializers.CELESTIAL_PAIR_SHAPED.get();
    }
}
