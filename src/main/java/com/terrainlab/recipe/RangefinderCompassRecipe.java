package com.terrainlab.recipe;

import com.terrainlab.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Rangefinding Compass recipe:
 * <pre>
 *   G Q G
 *   R C R
 *   G Q G
 * </pre>
 * G = Gold Ingot, Q = Quartz, R = Redstone, C = Compass. A lodestone target
 * on the input compass is copied to the result.
 *
 * <p>CraftingInput is already trimmed to its bounding box, so a 3x3 size
 * check needs no offset search.
 */
public class RangefinderCompassRecipe extends CustomRecipe {

    public static final RangefinderCompassRecipe INSTANCE = new RangefinderCompassRecipe();
    public static final com.mojang.serialization.MapCodec<RangefinderCompassRecipe> MAP_CODEC = com.mojang.serialization.MapCodec.unit(INSTANCE);
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, RangefinderCompassRecipe> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.unit(INSTANCE);

    public RangefinderCompassRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                ItemStack stack = input.getItem(x, y);
                boolean isCorner = (x == 0 || x == 2) && (y == 0 || y == 2);
                boolean isCenter = x == 1 && y == 1;
                boolean isCardinal = !isCorner && !isCenter;

                if (isCorner) {
                    if (!stack.is(Items.GOLD_INGOT)) return false;
                } else if (isCenter) {
                    if (!stack.is(Items.COMPASS)) return false;
                } else if (isCardinal) {
                    boolean topOrBottom = (y == 0 || y == 2);
                    if (topOrBottom) {
                        if (!stack.is(Items.QUARTZ)) return false;
                    } else {
                        if (!stack.is(Items.REDSTONE)) return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack compassStack = input.getItem(1, 1);

        ItemStack result = new ItemStack(ModItems.RANGEFINDER_COMPASS.get());
        LodestoneTracker tracker = compassStack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null) {
            result.set(DataComponents.LODESTONE_TRACKER, tracker);
        }
        return result;
    }

    @Override
    public RecipeSerializer<RangefinderCompassRecipe> getSerializer() {
        return com.terrainlab.registry.ModRecipeSerializers.RANGEFINDER_COMPASS.get();
    }
}
