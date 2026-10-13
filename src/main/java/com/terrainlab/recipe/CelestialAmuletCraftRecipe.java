package com.terrainlab.recipe;

import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.item.CelestialPearlItem;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Crafts a Celestial pearl surrounded by 8 Gold Ingots into that variant's
 * amulet, carrying over the pearl's pair id.
 */
public class CelestialAmuletCraftRecipe extends CustomRecipe {

    public static final CelestialAmuletCraftRecipe INSTANCE = new CelestialAmuletCraftRecipe();
    public static final com.mojang.serialization.MapCodec<CelestialAmuletCraftRecipe> MAP_CODEC = com.mojang.serialization.MapCodec.unit(INSTANCE);
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, CelestialAmuletCraftRecipe> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.unit(INSTANCE);

    public CelestialAmuletCraftRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
        if (input.width() != 3 || input.height() != 3) return false;

        ItemStack center = input.getItem(1, 1);
        if (variantOf(center) == null) return false;

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                if (x == 1 && y == 1) continue;
                ItemStack ring = input.getItem(x, y);
                if (ring.isEmpty() || !ring.is(Items.GOLD_INGOT)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack center = input.getItem(1, 1);
        CelestialVariant variant = variantOf(center);
        if (variant == null) return ItemStack.EMPTY;

        ItemStack result = new ItemStack(ModItems.amuletFor(variant));
        var pairId = center.get(ModDataComponents.CELESTIAL_PAIR_ID.get());
        if (pairId != null) result.set(ModDataComponents.CELESTIAL_PAIR_ID.get(), pairId);
        return result;
    }

    private static CelestialVariant variantOf(ItemStack stack) {
        if (!(stack.getItem() instanceof CelestialPearlItem pearlItem)) return null;
        return pearlItem.variant();
    }

    @Override
    public RecipeSerializer<CelestialAmuletCraftRecipe> getSerializer() {
        return com.terrainlab.registry.ModRecipeSerializers.CELESTIAL_AMULET_CRAFT.get();
    }
}
