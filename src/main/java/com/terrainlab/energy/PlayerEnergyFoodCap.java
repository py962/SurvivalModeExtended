package com.terrainlab.energy;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * Daily cap on Energy-tier food uses. The day is {@code getDayTime() / 24000}, matching the F3 day
 * counter. Hitting the cap only withholds the energy push; the item can still be eaten.
 */
public final class PlayerEnergyFoodCap {

    private PlayerEnergyFoodCap() {
    }

    /** Records a use and returns true, or returns false if today's cap is already reached. */
    public static boolean tryConsumeUse(ServerPlayer player) {
        long currentDay = player.level().getDayTime() / 24000L;
        long lastDay = player.getData(ModAttachments.PLAYER_ENERGY_FOOD_LAST_DAY.get());
        int usesToday = player.getData(ModAttachments.PLAYER_ENERGY_FOOD_USES_TODAY.get());

        if (currentDay != lastDay) {
            usesToday = 0;
            player.setData(ModAttachments.PLAYER_ENERGY_FOOD_LAST_DAY.get(), currentDay);
        }

        if (usesToday >= PlayerEnergyConstants.ENERGY_FOOD_DAILY_CAP) {
            player.setData(ModAttachments.PLAYER_ENERGY_FOOD_USES_TODAY.get(), usesToday);
            return false;
        }

        player.setData(ModAttachments.PLAYER_ENERGY_FOOD_USES_TODAY.get(), usesToday + 1);
        return true;
    }
}
