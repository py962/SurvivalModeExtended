package com.terrainlab.village;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;

/**
 * Computes a village's happiness as the fraction of members with no negative state
 * (thermal discomfort, fear, tiredness, trapped or hungry).
 */
public final class VillageHappinessService {

    private VillageHappinessService() {
    }

    public static double computeHappiness(List<Villager> members, long gameTime) {
        if (members.isEmpty()) {
            return 1.0;
        }

        int negative = 0;
        for (Villager villager : members) {
            VillagerThermalState thermal = villager.getData(ModAttachments.VILLAGER_THERMAL_STATE.get());
            boolean fearful = VillagerFearService.isFearful(villager, gameTime);
            boolean tired = VillagerTiredService.isTired(villager, gameTime);
            boolean trapped = VillagerTrappedService.isTrapped(villager);
            boolean hungry = VillagerMoodService.isHungry(villager);

            if (thermal != VillagerThermalState.COMFORTABLE || fearful || tired || trapped || hungry) {
                negative++;
            }
        }

        return 1.0 - (negative / (double) members.size());
    }
}
