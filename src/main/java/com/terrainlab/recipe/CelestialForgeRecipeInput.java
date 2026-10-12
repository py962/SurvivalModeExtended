package com.terrainlab.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** Celestial Forge input: the gem placed in the forge and a stack of thrown components. */
public record CelestialForgeRecipeInput(ItemStack core, ItemStack thrown) implements RecipeInput {

    public static final int CORE_SLOT = 0;
    public static final int THROWN_SLOT = 1;

    @Override
    public ItemStack getItem(int slot) {
        return slot == CORE_SLOT ? core : thrown;
    }

    @Override
    public int size() {
        return 2;
    }
}
