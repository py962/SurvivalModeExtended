package com.terrainlab.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.terrainlab.item.PotionMixing;
import com.terrainlab.registry.ModRecipeSerializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Brewing for Custom Potions (cauldron mixes): vanilla brewing recipes match on a base potion,
 * which a Custom Potion doesn't have. This matches the Custom Potion name instead and converts
 * the item (potion -> splash -> lingering) keeping every component, so the combined effects,
 * colour and name carry over. The output's item supplies its own duration scale (lingering).
 * <p>
 * Extends {@link BrewingRecipe} so the brewing stand's recipe check and the brewing
 * input/reagent property sets pick it up.
 */
public class CustomPotionBrewingRecipe extends BrewingRecipe {

    public static final MapCodec<CustomPotionBrewingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                            PotionIngredient.MAP_CODEC.fieldOf("input").forGetter(BrewingRecipe::getInput),
                            PotionIngredient.MAP_CODEC.fieldOf("reagent").forGetter(BrewingRecipe::getReagent),
                            ItemStackTemplate.CODEC.fieldOf("output").forGetter(BrewingRecipe::getOutput))
                    .apply(i, CustomPotionBrewingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, CustomPotionBrewingRecipe> STREAM_CODEC = StreamCodec.composite(
            PotionIngredient.STREAM_CODEC, BrewingRecipe::getInput,
            PotionIngredient.STREAM_CODEC, BrewingRecipe::getReagent,
            ItemStackTemplate.STREAM_CODEC, BrewingRecipe::getOutput,
            CustomPotionBrewingRecipe::new);

    public CustomPotionBrewingRecipe(PotionIngredient input, PotionIngredient reagent, ItemStackTemplate output) {
        super(input, reagent, output);
    }

    public static boolean isCustomPotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.customName().filter(PotionMixing.CUSTOM_POTION_NAME::equals).isPresent();
    }

    @Override
    public boolean matches(BrewingInput input, Level level) {
        return matches(input);
    }

    @Override
    public boolean matches(BrewingInput input) {
        return super.matches(input) && isCustomPotion(input.input());
    }

    @Override
    public ItemStack assemble(BrewingInput input) {
        return input.input().transmuteCopy(getOutput().create().getItem(), 1);
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<BrewingRecipe> getSerializer() {
        return (RecipeSerializer<BrewingRecipe>) (RecipeSerializer<?>) ModRecipeSerializers.CUSTOM_POTION_BREWING.get();
    }
}
