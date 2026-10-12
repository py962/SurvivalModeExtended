package com.terrainlab.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Lets zombies break doors on Normal as well as Hard by replacing Zombie's static
 * {@code DOOR_BREAKING_PREDICATE}. It must be swapped at the end of {@code <clinit>}, because each zombie's
 * BreakDoorGoal captures the predicate on construction. The per-zombie door-breaking spawn roll is unchanged.
 */
@Mixin(Zombie.class)
public abstract class ZombieNormalDoorBreakMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/ZombieDoorBreak]";

    @Shadow
    @Final
    @Mutable
    private static Predicate<Difficulty> DOOR_BREAKING_PREDICATE;

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void terrainlab$allowDoorBreakingOnNormal(CallbackInfo ci) {
        Predicate<Difficulty> original = DOOR_BREAKING_PREDICATE;
        DOOR_BREAKING_PREDICATE = difficulty -> difficulty == Difficulty.NORMAL || difficulty == Difficulty.HARD;

        // LOGGER.debug("{} DOOR_BREAKING_PREDICATE reassigned - zombies can now break doors on Normal and Hard (was Hard-only)",
        //         LOG_PREFIX);

    }
}
