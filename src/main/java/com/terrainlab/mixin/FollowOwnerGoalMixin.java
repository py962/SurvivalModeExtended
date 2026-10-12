package com.terrainlab.mixin;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops wolves and cats from following their owner; PetRoamingHandler keeps them near the player
 * or their bed instead. Parrots share this goal, so the check is by pet type.
 */
@Mixin(FollowOwnerGoal.class)
public abstract class FollowOwnerGoalMixin {

    @Shadow
    @Final
    private TamableAnimal tamable;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$noFollowStart(CallbackInfoReturnable<Boolean> cir) {
        if (tamable instanceof Wolf || tamable instanceof Cat) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$noFollowContinue(CallbackInfoReturnable<Boolean> cir) {
        if (tamable instanceof Wolf || tamable instanceof Cat) {
            cir.setReturnValue(false);
        }
    }
}
