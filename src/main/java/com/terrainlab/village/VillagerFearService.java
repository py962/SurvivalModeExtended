package com.terrainlab.village;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Per-villager fear state. Fear is set by taking damage or by seeing a hostile mob (both handled
 * in VillagerBehaviorHandler) and lasts {@link VillageConstants#FEAR_DECAY_TICKS} from the most
 * recent trigger. Never spreads between villagers.
 */
public final class VillagerFearService {

    private VillagerFearService() {
    }

    public static void onPossiblePanicTrigger(Villager villager, long gameTime) {
        villager.setData(ModAttachments.VILLAGER_LAST_PANIC_TICK.get(), gameTime);
    }

    public static boolean isFearful(Villager villager, long gameTime) {
        long lastPanic = villager.getData(ModAttachments.VILLAGER_LAST_PANIC_TICK.get());
        if (lastPanic < 0) {
            return false;
        }
        long elapsed = gameTime - lastPanic;
        return elapsed >= 0 && elapsed < VillageConstants.FEAR_DECAY_TICKS;
    }
}
