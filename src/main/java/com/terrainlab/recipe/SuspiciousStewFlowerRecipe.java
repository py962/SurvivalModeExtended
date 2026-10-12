package com.terrainlab.recipe;

import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffects;

/**
 * Bowl + Brown Mushroom + Red Mushroom + Dandelion, Blue Orchid or Oxeye
 * Daisy produces a Suspicious Stew with a remapped effect (Warmth, energy
 * boost, instant heal respectively). Other flowers use vanilla's recipes.
 *
 * <p>The ingredients overlap vanilla's per-flower stew recipes, so this
 * recipe only applies if it is ordered ahead of them (recipe priority or
 * vanilla recipe override).
 */
public class SuspiciousStewFlowerRecipe extends CustomRecipe {

    public SuspiciousStewFlowerRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean bowl = false, brown = false, red = false;
        String flower = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(Items.BOWL)) bowl = true;
            else if (s.is(Items.BROWN_MUSHROOM)) brown = true;
            else if (s.is(Items.RED_MUSHROOM)) red = true;
            else if (s.is(Items.DANDELION)) flower = "dandelion";
            else if (s.is(Items.BLUE_ORCHID)) flower = "blue_orchid";
            else if (s.is(Items.OXEYE_DAISY)) flower = "oxeye_daisy";
            else return false;
        }
        return bowl && brown && red && flower != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        String flower = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.is(Items.DANDELION)) flower = "dandelion";
            else if (s.is(Items.BLUE_ORCHID)) flower = "blue_orchid";
            else if (s.is(Items.ALLIUM)) flower = "allium";
            else if (s.is(Items.OXEYE_DAISY)) flower = "oxeye_daisy";
        }
        if (flower == null) return ItemStack.EMPTY;

        ItemStack result = new ItemStack(Items.SUSPICIOUS_STEW);
        switch (flower) {
            case "dandelion" -> result.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(
                    java.util.List.of(new SuspiciousStewEffects.Entry(
                            com.terrainlab.registry.ModMobEffects.WARMTH, SuspiciousStewEffects.DEFAULT_DURATION))));
            case "allium" -> result.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, new SuspiciousStewEffects(
                    java.util.List.of(new SuspiciousStewEffects.Entry(
                            MobEffects.FIRE_RESISTANCE, SuspiciousStewEffects.DEFAULT_DURATION))));
            default -> { // blue_orchid, oxeye_daisy: no MobEffect; ModFoodEffectsHandler reads the marker
                result.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
                result.set(ModDataComponents.STEW_FLOWER.get(), flower);
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<SuspiciousStewFlowerRecipe> getSerializer() {
        return com.terrainlab.registry.ModRecipeSerializers.SUSPICIOUS_STEW_FLOWER.get();
    }
}
