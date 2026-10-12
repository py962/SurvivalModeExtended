package com.terrainlab.village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;

/**
 * Daily village hunger check: compares the food carried by all members against the
 * population's daily requirement and records the result on the {@link VillageRecord}.
 */
public final class VillageHungerService {

    private VillageHungerService() {
    }

    public static void runDailyCheck(ServerLevel level, VillageId id, List<Villager> members) {
        VillageRecord record = VillageSavedData.get(level).getOrCreate(id);

        int totalFood = 0;
        int farmerCount = 0;
        for (Villager villager : members) {
            // Whole carried food store, not foodLevel, which is near zero outside breeding.
            totalFood += VillagerFoodAccess.getFoodPoints(villager);
            if (VillagerProfessionAccess.getProfessionKey(villager).equals("farmer")) {
                farmerCount++;
            }
        }

        int required = members.size() * VillageConstants.FOOD_PER_VILLAGER_PER_DAY;
        VillageHungerState state = totalFood >= required ? VillageHungerState.SUFFICIENT : VillageHungerState.HUNGRY;

        record.setFoodStock(totalFood);
        record.setRequiredFoodStock(required);
        record.setHungerState(state);
        record.setPopulationLastCheck(members.size());
        record.setFarmerCountLastCheck(farmerCount);
    }
}
