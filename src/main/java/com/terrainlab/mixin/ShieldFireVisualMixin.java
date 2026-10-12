
package com.terrainlab.mixin;

import com.terrainlab.event.ShieldCombatEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ShieldFireVisualMixin {

    @Inject(
            method = "displayFireAnimation",
            at = @At("HEAD"),
            cancellable = true
    )
    private void terrainlab$suppressShieldFireAnimation(
            CallbackInfoReturnable<Boolean> cir) {

        Entity entity = (Entity) (Object) this;

        if (entity instanceof LivingEntity living
                && ShieldCombatEvents.shouldSuppressBurningVisual(living)) {
            cir.setReturnValue(false);
        }
    }
}

