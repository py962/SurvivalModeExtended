package com.terrainlab.recipe;

import com.terrainlab.item.PotionMixing;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;

/**
 * Brewing for Custom Potions (cauldron mixes): vanilla's container mixes rebuild the output from
 * the base potion, which a Custom Potion doesn't have (vanilla would eat the reagent and return
 * the potion unchanged). This converts the item (potion -> splash -> lingering) keeping every
 * component, so the combined effects, colour and name carry over. The output's item supplies its
 * own duration scale (lingering). Checked before vanilla's mixes (NeoForge recipe registry).
 */
public class CustomPotionBrewingRecipe implements IBrewingRecipe {

    private final Item input;
    private final Item reagent;
    private final Item output;

    public CustomPotionBrewingRecipe(Item input, Item reagent, Item output) {
        this.input = input;
        this.reagent = reagent;
        this.output = output;
    }

    public static boolean isCustomPotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.customName().filter(PotionMixing.CUSTOM_POTION_NAME::equals).isPresent();
    }

    @Override
    public boolean isInput(ItemStack stack) {
        return stack.is(input) && isCustomPotion(stack);
    }

    @Override
    public boolean isIngredient(ItemStack stack) {
        return stack.is(reagent);
    }

    @Override
    public ItemStack getOutput(ItemStack inputStack, ItemStack ingredient) {
        if (!isInput(inputStack) || !isIngredient(ingredient)) {
            return ItemStack.EMPTY;
        }
        return inputStack.transmuteCopy(output, 1);
    }
}
