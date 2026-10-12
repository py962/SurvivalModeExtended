package com.terrainlab.mixin;

import com.terrainlab.registry.ModMobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels {@code jumpFromGround} while Phasing is active. With noPhysics set, onGround() never clears, so
 * jumps would otherwise repeat while Space is held; this overlaps PhasingEffectHandler's per-tick Y clamp.
 */
@Mixin(LivingEntity.class)
public abstract class PhasingBlockJumpMixin {

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockJumpWhilePhasing(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.hasEffect(ModMobEffects.PHASING)) {
            ci.cancel();
        }
    }
}
