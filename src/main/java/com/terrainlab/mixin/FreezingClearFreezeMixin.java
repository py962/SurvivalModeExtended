package com.terrainlab.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the Freezing effect's powder-snow state alive against vanilla's per-tick resets.
 * <ul>
 *   <li>clearFreeze() runs whenever the entity is not inside real powder snow; it is cancelled
 *       while Freezing is active (Warmth ends Freezing, so its own clearFreeze still works).</li>
 *   <li>baseTick() resets isInPowderSnow at its start, after FreezingEffectHandler set it on
 *       EntityTickEvent.Pre; the flag is restored right after that reset.</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class FreezingClearFreezeMixin {

    @Inject(method = "clearFreeze", at = @At("HEAD"), cancellable = true)
    private void terrainlab$preventClearWhileFreezing(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof net.minecraft.world.entity.LivingEntity living
                && com.terrainlab.event.FreezingEffectHandler.isFreezing(living)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "baseTick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;isInPowderSnow:Z",
                    opcode = org.objectweb.asm.Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void terrainlab$forceInPowderSnowWhileFreezing(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof net.minecraft.world.entity.LivingEntity living
                && com.terrainlab.event.FreezingEffectHandler.isFreezing(living)) {
            self.isInPowderSnow = true;
        }
    }
}
