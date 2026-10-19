package com.terrainlab.village;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Reads a villager's food store. Vanilla keeps eaten food in the private {@code foodLevel}
 * field, accessed here by reflection (Mojang names at runtime).
 */
public final class VillagerFoodAccess {

    private static final Field FOOD_LEVEL_FIELD = resolveFoodLevelField();

    private VillagerFoodAccess() {
    }

    /**
     * Total food in vanilla food points: eaten {@code foodLevel} plus inventory food valued with
     * {@link Villager#FOOD_POINTS}. The inventory is the real store; {@code foodLevel} is
     * usually 0 because villagers only eat right before breeding.
     */
    public static int getFoodPoints(Villager villager) {
        int points = getFoodLevel(villager);
        SimpleContainer inventory = villager.getInventory();
        for (Map.Entry<Item, Integer> food : Villager.FOOD_POINTS.entrySet()) {
            points += inventory.countItem(food.getKey()) * food.getValue();
        }
        return points;
    }

    public static int getFoodLevel(Villager villager) {
        if (FOOD_LEVEL_FIELD == null) {
            return 0;
        }
        try {
            return FOOD_LEVEL_FIELD.getInt(villager);
        } catch (IllegalAccessException e) {
            com.terrainlab.TerrainLab.LOGGER.warn("[VILLAGER_FOOD_ACCESS] getInt failed on {}: {}", villager, e.toString());
            return 0;
        }
    }

    /** Upper bound for {@link #addFoodLevel}; well inside vanilla's byte save format. */
    public static final int MAX_FOOD_LEVEL = 64;

    /**
     * Adds eaten food points to vanilla {@code foodLevel}, capped at {@link #MAX_FOOD_LEVEL} (vanilla
     * saves it as a byte). Vanilla also spends this store on breeding (12 points per attempt).
     */
    public static void addFoodLevel(Villager villager, int points) {
        if (FOOD_LEVEL_FIELD == null || points <= 0) {
            return;
        }
        try {
            int current = FOOD_LEVEL_FIELD.getInt(villager);
            FOOD_LEVEL_FIELD.setInt(villager, Math.min(MAX_FOOD_LEVEL, current + points));
        } catch (IllegalAccessException e) {
            com.terrainlab.TerrainLab.LOGGER.warn("[VILLAGER_FOOD_ACCESS] setInt failed on {}: {}", villager, e.toString());
        }
    }

    private static Field resolveFoodLevelField() {
        try {
            Field f = Villager.class.getDeclaredField("foodLevel");
            f.setAccessible(true);
            // com.terrainlab.TerrainLab.LOGGER.info("[VILLAGER_FOOD_ACCESS] resolved Villager#foodLevel by expected name");
            return f;
        } catch (NoSuchFieldException expectedNameMiss) {
            // Fallback: first private int field declared on Villager.
            for (Field f : Villager.class.getDeclaredFields()) {
                if (f.getType() == int.class && java.lang.reflect.Modifier.isPrivate(f.getModifiers())) {
                    f.setAccessible(true);
                    com.terrainlab.TerrainLab.LOGGER.warn(
                            "[VILLAGER_FOOD_ACCESS] 'foodLevel' not found by that name - falling back to guessed field '{}'. "
                                    + "VERIFY this is correct against your jar.", f.getName());
                    return f;
                }
            }
            com.terrainlab.TerrainLab.LOGGER.error(
                    "[VILLAGER_FOOD_ACCESS] could not resolve Villager's internal food field at all - "
                            + "§4 village hunger will always read 0 food stock until this is fixed.");
            return null;
        }
    }
}
