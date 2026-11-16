package com.terrainlab.village;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.VillagerFood;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;

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
     * the VILLAGER_FOOD component. The inventory is the real store; {@code foodLevel} is
     * usually 0 because villagers only eat right before breeding.
     */
    public static int getFoodPoints(Villager villager) {
        int points = getFoodLevel(villager);
        SimpleContainer inventory = villager.getInventory();
        // 26.x: food values come from the VILLAGER_FOOD component (mirrors Villager#countFoodPointsInInventory).
        for (ItemStack stack : inventory.getItems()) {
            VillagerFood food = stack.get(DataComponents.VILLAGER_FOOD);
            if (food != null) {
                points += stack.getCount() * food.nutrition();
            }
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

    /** Spends eaten food first, then inventory food; change from the last item goes back to foodLevel. */
    public static void spendFood(Villager villager, int points) {
        if (points <= 0) {
            return;
        }
        int level = getFoodLevel(villager);
        int fromLevel = Math.min(level, points);
        if (fromLevel > 0) {
            setFoodLevel(villager, level - fromLevel);
            points -= fromLevel;
        }
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && points > 0; slot++) {
            net.minecraft.world.item.ItemStack stack = inventory.getItem(slot);
            VillagerFood food = stack.get(DataComponents.VILLAGER_FOOD);
            int value = food == null ? 0 : food.nutrition();
            while (value > 0 && !stack.isEmpty() && points > 0) {
                stack.shrink(1);
                points -= value;
            }
        }
        if (points < 0) {
            addFoodLevel(villager, -points);
        }
    }

    private static void setFoodLevel(Villager villager, int value) {
        if (FOOD_LEVEL_FIELD == null) {
            return;
        }
        try {
            FOOD_LEVEL_FIELD.setInt(villager, Math.max(0, Math.min(MAX_FOOD_LEVEL, value)));
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
