package com.terrainlab.temperature;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Instant internal-temperature pushes from Cool and Warmth category foods.
 */
public final class FoodTemperatureEffects {

    private static final Map<Item, Double> PUSH_BY_ITEM = new HashMap<>();

    static {
        PUSH_BY_ITEM.put(Items.MELON_SLICE, PlayerTemperatureConstants.COOLING_FOOD_PUSH);
        PUSH_BY_ITEM.put(Items.MUSHROOM_STEW, PlayerTemperatureConstants.WARMING_FOOD_PUSH);
        PUSH_BY_ITEM.put(Items.RABBIT_STEW, PlayerTemperatureConstants.WARMING_FOOD_PUSH);
        PUSH_BY_ITEM.put(Items.BEETROOT_SOUP, PlayerTemperatureConstants.WARMING_FOOD_PUSH);
    }

    private FoodTemperatureEffects() {
    }

    /**
     * Temperature push for an item matched by identity, or null. Water bottles need a component check; see
     * {@link #isWaterBottle}.
     */
    public static Double getDirectPush(Item item) {
        return PUSH_BY_ITEM.get(item);
    }

    public static boolean isWaterBottle(ItemStack stack) {
        var potionContents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        return potionContents != null && potionContents.is(net.minecraft.world.item.alchemy.Potions.WATER);
    }
}
