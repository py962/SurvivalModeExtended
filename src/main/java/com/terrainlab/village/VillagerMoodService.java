package com.terrainlab.village;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Folds a villager's discomfort states (cold, hot, fearful, tired, trapped, hungry, injured) into a bitmask
 * stored in the synced {@code VILLAGER_MOOD_FLAGS} attachment for client-side indicators. Updated
 * on the staggered per-villager read; writes only when the mask changes.
 */
public final class VillagerMoodService {

    public static final int COLD = 1;
    public static final int HOT = 1 << 1;
    public static final int FEARFUL = 1 << 2;
    public static final int TIRED = 1 << 3;
    public static final int TRAPPED = 1 << 4;
    public static final int HUNGRY = 1 << 5;
    public static final int INJURED = 1 << 6;

    /** Display priority when several states apply; the face and posture show only one. */
    private static final int[] PRIORITY = {FEARFUL, INJURED, COLD, HOT, TIRED, HUNGRY, TRAPPED};

    private VillagerMoodService() {
    }

    /** An adult whose own food store (eaten + carried) is below one day's need. Babies are never hungry. */
    public static boolean isHungry(Villager villager) {
        return !villager.isBaby()
                && VillagerFoodAccess.getFoodPoints(villager) < VillageConstants.FOOD_PER_VILLAGER_PER_DAY;
    }

    /** Below half of its maximum health (see VillagerHealthService for how villagers heal). */
    public static boolean isInjured(Villager villager) {
        return villager.isAlive() && villager.getHealth() < villager.getMaxHealth() * 0.5F;
    }

    /** The single state the face and posture show, or 0 for none. */
    public static int primary(int flags) {
        for (int state : PRIORITY) {
            if ((flags & state) != 0) {
                return state;
            }
        }
        return 0;
    }

    public static void update(Villager villager, long gameTime) {
        int flags = 0;

        VillagerThermalState thermal = villager.getData(ModAttachments.VILLAGER_THERMAL_STATE.get());
        if (thermal == VillagerThermalState.COLD) {
            flags |= COLD;
        } else if (thermal == VillagerThermalState.HOT) {
            flags |= HOT;
        }

        if (VillagerFearService.isFearful(villager, gameTime)) {
            flags |= FEARFUL;
        }
        if (VillagerTiredService.isTired(villager, gameTime)) {
            flags |= TIRED;
        }
        if (VillagerTrappedService.isTrapped(villager)) {
            flags |= TRAPPED;
        }
        if (isHungry(villager)) {
            flags |= HUNGRY;
        }
        if (isInjured(villager)) {
            flags |= INJURED;
        }

        if (villager.getData(ModAttachments.VILLAGER_MOOD_FLAGS.get()) != flags) {
            villager.setData(ModAttachments.VILLAGER_MOOD_FLAGS.get(), flags);
        }
    }
}
