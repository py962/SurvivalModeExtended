package com.terrainlab.food;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps food items to their {@link FoodCategory} and per-item heal/hunger values. Unlisted items,
 * including Golden Apple and Enchanted Golden Apple, keep vanilla food behaviour.
 */
public final class FoodCategoryTable {

    public record HealthData(float amount, int durationTicks) {} // durationTicks 0 = instant

    private static final Map<Item, FoodCategory> CATEGORY = new HashMap<>();
    private static final Map<Item, HealthData> HEALTH_VALUES = new HashMap<>();
    private static final Map<Item, Integer> HUNGER_POINTS = new HashMap<>();

    static {
        // HEALTH: zero hunger, custom healing.
        put(Items.DRIED_KELP, FoodCategory.HEALTH);
        HEALTH_VALUES.put(Items.DRIED_KELP, new HealthData(1.0F, 200));  // 1/2 heart over 10s
        put(Items.CARROT, FoodCategory.HEALTH);
        HEALTH_VALUES.put(Items.CARROT, new HealthData(2.0F, 300));      // 1 heart over 15s
        put(Items.APPLE, FoodCategory.HEALTH);
        HEALTH_VALUES.put(Items.APPLE, new HealthData(4.0F, 400));       // 2 heart over 20s
        put(Items.GOLDEN_CARROT, FoodCategory.HEALTH);
        HEALTH_VALUES.put(Items.GOLDEN_CARROT, new HealthData(6.0F, 0)); // 3 hearts instant

        // COOL / WARMTH: temperature effects live in FoodTemperatureEffects; zero hunger here.
        put(Items.MELON_SLICE, FoodCategory.COOL);
        put(Items.MUSHROOM_STEW, FoodCategory.WARMTH);
        put(Items.RABBIT_STEW, FoodCategory.WARMTH);
        put(Items.BEETROOT_SOUP, FoodCategory.WARMTH);

        // ENERGY
        put(Items.COCOA_BEANS, FoodCategory.ENERGY);
        // Blue Orchid Suspicious Stew is component-based and handled outside this table.

        // HUNGER_1: snack, 2 points
        for (Item i : new Item[]{Items.COOKIE, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.CAKE}) {
            put(i, FoodCategory.HUNGER_1);
            HUNGER_POINTS.put(i, 2);
        }
        // HUNGER_2: light meal, 6 points
        for (Item i : new Item[]{Items.BREAD, Items.BAKED_POTATO, Items.PUMPKIN_PIE, Items.COOKED_COD, Items.COOKED_SALMON, Items.COOKED_CHICKEN, Items.COOKED_RABBIT}) {
            put(i, FoodCategory.HUNGER_2);
            HUNGER_POINTS.put(i, 6);
        }
        // HUNGER_3: standard meal, 10 points
        for (Item i : new Item[]{Items.COOKED_MUTTON, Items.COOKED_PORKCHOP, Items.COOKED_BEEF}) {
            put(i, FoodCategory.HUNGER_3);
            HUNGER_POINTS.put(i, 10);
        }
        put(com.terrainlab.registry.ModItems.COOKED_VENISON.get(), FoodCategory.HUNGER_3);
        HUNGER_POINTS.put(com.terrainlab.registry.ModItems.COOKED_VENISON.get(), 10);

        // RISKY items are intentionally absent.
    }

    private static void put(Item item, FoodCategory category) {
        CATEGORY.put(item, category);
    }

    public static FoodCategory getCategory(Item item) {
        return CATEGORY.get(item); // null = vanilla behaviour
    }

    public static HealthData getHealthData(Item item) {
        return HEALTH_VALUES.get(item);
    }

    public static Integer getHungerPoints(Item item) {
        return HUNGER_POINTS.get(item);
    }
}
