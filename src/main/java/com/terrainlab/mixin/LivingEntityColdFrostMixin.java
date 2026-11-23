package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.terrainlab.event.FreezingEffectHandler;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Freezing from low body temperature isn't stopped by leather armour, so the frost overlay still
 * builds up. Leather keeps protecting against real powder snow; freeze damage is untouched.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityColdFrostMixin {

    // The first canFreeze() in aiStep gates the frost thaw; the second gates freeze damage.
    @ModifyExpressionValue(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;canFreeze()Z", ordinal = 0),
            require = 1
    )
    private boolean terrainlab$coldIgnoresLeather(boolean canFreeze) {
        LivingEntity self = (LivingEntity) (Object) this;
        return canFreeze || (FreezingEffectHandler.isFreezing(self) && FreezingEffectHandler.isColdExposureOnly(self));
    }
}
