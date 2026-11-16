package com.terrainlab.village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/** Daily food use for adult villagers; at most one day is charged however long a villager was unloaded. */
public final class VillagerDailyFoodService {

    private VillagerDailyFoodService() {
    }

    public static final int FOOD_USED_PER_DAY = 4;

    private static final String LAST_DAY_KEY = "terrainlab_food_day";

    public static void update(Villager villager, ServerLevel level) {
        if (villager.isBaby()) {
            return;
        }
        long day = Math.floorDiv(level.getOverworldClockTime(), VillageConstants.DAY_LENGTH_TICKS);
        CompoundTag data = villager.getPersistentData();
        long last = data.getLongOr(LAST_DAY_KEY, -1L);
        if (last >= 0L && day > last) {
            VillagerFoodAccess.spendFood(villager, FOOD_USED_PER_DAY);
        }
        if (day != last) {
            data.putLong(LAST_DAY_KEY, day);
        }
    }
}
