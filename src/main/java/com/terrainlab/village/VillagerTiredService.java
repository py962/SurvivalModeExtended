package com.terrainlab.village;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Per-villager tiredness. A villager becomes tired once {@link VillageConstants#TIRED_THRESHOLD_TICKS}
 * pass without a completed sleep, detected as the falling edge of {@code isSleeping()} between
 * staggered reads (accurate to one read interval).
 */
public final class VillagerTiredService {

    private VillagerTiredService() {
    }

    public static void update(Villager villager, long gameTime) {
        boolean sleepingNow = villager.isSleeping();
        boolean wasSleeping = villager.getData(ModAttachments.VILLAGER_WAS_SLEEPING.get());
        long lastSlept = villager.getData(ModAttachments.VILLAGER_LAST_SLEPT_TICK.get());

        if (lastSlept < 0) {
            // First read: treat as just slept so new villagers are not instantly tired.
            lastSlept = gameTime;
        } else if (wasSleeping && !sleepingNow) {
            lastSlept = gameTime;
        }

        villager.setData(ModAttachments.VILLAGER_WAS_SLEEPING.get(), sleepingNow);
        villager.setData(ModAttachments.VILLAGER_LAST_SLEPT_TICK.get(), lastSlept);
    }

    public static boolean isTired(Villager villager, long gameTime) {
        long lastSlept = villager.getData(ModAttachments.VILLAGER_LAST_SLEPT_TICK.get());
        if (lastSlept < 0) {
            return false;
        }
        return (gameTime - lastSlept) > VillageConstants.TIRED_THRESHOLD_TICKS;
    }
}
