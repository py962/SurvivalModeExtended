package com.terrainlab.recipe;

import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.item.CelestialPearlItem;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Crafts a Celestial pearl surrounded by 8 of its variant's frame material
 * into that variant's conduit item, copying the pair id and variant
 * components onto the result.
 *
 * <p>BREATH yields vanilla's {@code minecraft:conduit} (see
 * ModItems#conduitItemFor); the vanilla conduit recipe is disabled. Vanilla
 * keeps unconsumed components on the placed block entity, which is where
 * BreathConduitHooks reads the pair id back.
 */
public class CelestialConduitCraftRecipe extends CustomRecipe {

    public CelestialConduitCraftRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
        if (input.width() != 3 || input.height() != 3) return false;

        ItemStack center = input.getItem(1, 1);
        CelestialVariant variant = variantOf(center);
        if (variant == null) return false;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x == 1 && y == 1) continue;
                ItemStack ring = input.getItem(x, y);
                if (ring.isEmpty() || !ring.is(variant.frameBlock().asItem())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack center = input.getItem(1, 1);
        CelestialVariant variant = variantOf(center);
        if (variant == null) return ItemStack.EMPTY;

        ItemStack result = new ItemStack(ModItems.conduitItemFor(variant));
        copyPairData(center, result);
        return result;
    }

    private static CelestialVariant variantOf(ItemStack stack) {
        if (!(stack.getItem() instanceof CelestialPearlItem pearlItem)) return null;
        return pearlItem.variant();
    }

    private static void copyPairData(ItemStack from, ItemStack to) {
        var pairId = from.get(ModDataComponents.CELESTIAL_PAIR_ID.get());
        if (pairId != null) to.set(ModDataComponents.CELESTIAL_PAIR_ID.get(), pairId);
        var variant = from.get(ModDataComponents.CELESTIAL_VARIANT.get());
        if (variant != null) to.set(ModDataComponents.CELESTIAL_VARIANT.get(), variant);
    }

    @Override
    public RecipeSerializer<CelestialConduitCraftRecipe> getSerializer() {
        return com.terrainlab.registry.ModRecipeSerializers.CELESTIAL_CONDUIT_CRAFT.get();
    }
}
