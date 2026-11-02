package com.terrainlab.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hunger rules in {@code FoodData#tick}:
 * <ul>
 *   <li>Peaceful and Normal (and so Isolation) report as Hard here, so hunger depletes and starvation
 *       can kill, as on vanilla Hard (mob spawning, which reads difficulty elsewhere, is unaffected).
 *       Easy keeps vanilla's rule (starvation stops at 5 hearts).</li>
 *   <li>Natural regeneration from food is limited to Easy; the game rule still applies there.</li>
 * </ul>
 */
@Mixin(FoodData.class)
public abstract class PeacefulMatchesNormalMixin {

    @Redirect(
            method = "tick(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getDifficulty()Lnet/minecraft/world/Difficulty;"
            ),
            require = 1
    )
    private Difficulty terrainlab$treatPeacefulAsNormal(ServerLevel level) {
        Difficulty real = level.getDifficulty();
        return real == Difficulty.PEACEFUL || real == Difficulty.NORMAL ? Difficulty.HARD : real;
    }

    // The only unboxing in tick() is the NATURAL_HEALTH_REGENERATION game-rule read.
    @Redirect(
            method = "tick(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Boolean;booleanValue()Z"

            ),
            require = 1
    )
    private boolean terrainlab$restrictNaturalRegenToEasy(Boolean flag, ServerPlayer player) {
        Difficulty difficulty = player.level().getDifficulty();
        return flag && difficulty == Difficulty.EASY;
    }
}
