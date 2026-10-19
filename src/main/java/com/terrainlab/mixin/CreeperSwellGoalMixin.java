
        package com.terrainlab.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SwellGoal.class)
public abstract class CreeperSwellGoalMixin {

    @Shadow
    @Nullable
    private LivingEntity target;

    @Shadow
    private net.minecraft.world.entity.monster.Creeper creeper;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void terrainlab$ignoreLineOfSight(CallbackInfo ci) {
        if (this.target != null && !this.target.isDeadOrDying()) {
            if (this.creeper.distanceToSqr(this.target) > 49.0) {
                this.creeper.setSwellDir(-1);
            } else {
                this.creeper.setSwellDir(1);
            }
        } else {
            this.creeper.setSwellDir(-1);
        }

        ci.cancel();
    }
}

