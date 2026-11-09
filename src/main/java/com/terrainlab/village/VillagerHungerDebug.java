package com.terrainlab.village;

import com.terrainlab.TerrainLab;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * Temporary debug logging for villager hunger, all lines tagged [VillagerHunger]. Set ENABLED to
 * false (or delete the calls) once the cause of villagers going hungry again is found.
 * <ul>
 * <li>every change in a villager's eaten food, carried food or hungry state between its 5-second
 *     reads (so any drop shows when and by how much);</li>
 * <li>every chest meal, with the food store before and after;</li>
 * <li>why a hungry villager didn't eat, logged when the reason changes;</li>
 * <li>vanilla's breeding food use (12 points), and the starting bread.</li>
 * </ul>
 */
public final class VillagerHungerDebug {

    public static final boolean ENABLED = true;

    /** Last read per villager: eaten, carried, hungry (0/1). */
    private static final Map<Villager, int[]> LAST = new WeakHashMap<>();
    private static final Map<Villager, String> LAST_SKIP = new WeakHashMap<>();

    private VillagerHungerDebug() {
    }

    private static String label(Villager villager) {
        return "villager " + villager.getId() + " (" + VillagerProfessionAccess.getProfessionKey(villager)
                + (villager.isBaby() ? ", baby" : "") + ") at "
                + villager.blockPosition().getX() + " " + villager.blockPosition().getY() + " "
                + villager.blockPosition().getZ() + " t=" + villager.level().getGameTime();
    }

    private static int carried(Villager villager) {
        return VillagerFoodAccess.getFoodPoints(villager) - VillagerFoodAccess.getFoodLevel(villager);
    }

    /** Called at the end of each mood read: actual hunger, and whether it is shown (a day or more). */
    public static void observe(Villager villager, boolean hungry, boolean shown) {
        if (!ENABLED) return;
        int eaten = VillagerFoodAccess.getFoodLevel(villager);
        int carried = carried(villager);
        int[] now = {eaten, carried, hungry ? 1 : 0, shown ? 1 : 0};
        int[] before = LAST.put(villager, now);
        if (before == null) {
            TerrainLab.LOGGER.info("[VillagerHunger] {} first read: eaten {}, carried {}, hungry {}, shown {}",
                    label(villager), eaten, carried, hungry, shown);
            return;
        }
        if (before[0] != eaten || before[1] != carried || before[2] != now[2] || before[3] != now[3]) {
            TerrainLab.LOGGER.info("[VillagerHunger] {} changed: eaten {} -> {}, carried {} -> {}, hungry {} -> {}, shown {} -> {}",
                    label(villager), before[0], eaten, before[1], carried, before[2] == 1, hungry,
                    before[3] == 1, shown);
        }
    }

    /** A hungry villager that didn't eat this read; logged only when the reason changes. */
    public static void skip(Villager villager, String reason) {
        if (!ENABLED) return;
        String before = LAST_SKIP.put(villager, reason);
        if (!reason.equals(before)) {
            TerrainLab.LOGGER.info("[VillagerHunger] {} hungry but didn't eat: {}", label(villager), reason);
        }
    }

    public static void meal(Villager villager, ItemStack food, int storeBefore, int storeAfter) {
        if (!ENABLED) return;
        LAST_SKIP.remove(villager);
        TerrainLab.LOGGER.info("[VillagerHunger] {} ate {} from the chest: store {} -> {}",
                label(villager), food.getItem(), storeBefore, storeAfter);
    }

    /** Vanilla breeding: eats carried food up to 12, then uses 12 points. Called before it runs. */
    public static void digest(Villager villager) {
        if (!ENABLED) return;
        TerrainLab.LOGGER.info("[VillagerHunger] {} breeding attempt, vanilla uses 12 food: eaten {}, carried {} before",
                label(villager), VillagerFoodAccess.getFoodLevel(villager), carried(villager));
    }

    public static void startingFood(Villager villager, int loaves) {
        if (!ENABLED) return;
        TerrainLab.LOGGER.info("[VillagerHunger] {} given {} starting bread", label(villager), loaves);
    }
}
