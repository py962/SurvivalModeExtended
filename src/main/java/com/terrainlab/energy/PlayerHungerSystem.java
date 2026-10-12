package com.terrainlab.energy;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;

import static com.terrainlab.energy.PlayerEnergyConstants.*;

/**
 * Flat-rate hunger drain (1 point per TICKS_PER_HUNGER_DRAIN), independent of activity.
 *
 * <p>Saturation is pinned at SATURATION_BUFFER_LEVEL so vanilla exhaustion never reaches hunger.
 * On Easy the drain floors at 1, so vanilla starvation (which needs foodLevel 0) never fires. On
 * Peaceful a separate timer applies non-lethal starvation damage at vanilla's 80-tick cadence.
 */
public final class PlayerHungerSystem {

    private static final int PEACEFUL_STARVE_DAMAGE_INTERVAL_TICKS = 80; // vanilla cadence

    private PlayerHungerSystem() {
    }

    public static void tick(ServerPlayer player) {
        // Paused in creative/spectator.
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        player.getFoodData().setSaturation(SATURATION_BUFFER_LEVEL);

        Difficulty difficulty = player.level().getDifficulty();
        int floor = (difficulty == Difficulty.EASY) ? 1 : 0;

        int counter = player.getData(ModAttachments.PLAYER_HUNGER_DRAIN_COUNTER.get()) + 1;
        if (counter >= TICKS_PER_HUNGER_DRAIN) {
            counter = 0;
            int hunger = player.getFoodData().getFoodLevel();
            player.getFoodData().setFoodLevel(Math.max(floor, hunger - 1));
        }
        player.setData(ModAttachments.PLAYER_HUNGER_DRAIN_COUNTER.get(), counter);
        // Starvation damage, Peaceful included, comes from vanilla FoodData (see PeacefulMatchesNormalMixin).
    }
}
