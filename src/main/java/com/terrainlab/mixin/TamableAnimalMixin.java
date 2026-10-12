package com.terrainlab.mixin;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables teleport-to-owner for wolves and cats, including the panic-goal teleport, so anchored pets stay
 * put. PetSafetyTeleportHandler moves pets out of real hazards instead.
 */
@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {

    @Inject(method = "shouldTryTeleportToOwner", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$noOwnerTeleport(CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof Wolf || self instanceof Cat) {
            cir.setReturnValue(false);
        }
    }
}
