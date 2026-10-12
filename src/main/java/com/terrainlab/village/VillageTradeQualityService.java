package com.terrainlab.village;

import net.minecraft.world.entity.npc.villager.Villager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trade quality: the sum, over each distinct profession present, of its highest villager level.
 */
public final class VillageTradeQualityService {

    private VillageTradeQualityService() {
    }

    public static int computeScore(List<Villager> members) {
        Map<String, Integer> bestLevelByProfession = new HashMap<>();

        for (Villager villager : members) {
            String professionKey = VillagerProfessionAccess.getProfessionKey(villager);
            if (professionKey.equals("none") || professionKey.equals("nitwit") || professionKey.equals("unknown")) {
                continue;
            }
            int level = VillagerProfessionAccess.getLevel(villager);
            bestLevelByProfession.merge(professionKey, level, Math::max);
        }

        return bestLevelByProfession.values().stream().mapToInt(Integer::intValue).sum();
    }
}
