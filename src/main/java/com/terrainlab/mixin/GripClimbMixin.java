package com.terrainlab.mixin;

import com.terrainlab.registry.ModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets entities with Grip climb any wall they are pushing against. */
@Mixin(LivingEntity.class)
public abstract class GripClimbMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void terrainlab$gripClimb(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isSpectator()) {
            return;
        }
        if (self.horizontalCollision && self.hasEffect(ModMobEffects.GRIP)) {
            cir.setReturnValue(true);
        }
    }
}
