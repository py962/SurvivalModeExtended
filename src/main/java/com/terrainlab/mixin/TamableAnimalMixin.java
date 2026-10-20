package com.terrainlab.mixin;

import com.terrainlab.pet.AnchoredPets;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Disables teleport-to-owner for anchored pets (wolves, cats, geese, owls, vultures), including the panic-goal teleport, so anchored pets stay
 * put. PetSafetyTeleportHandler moves pets out of real hazards instead.
 */
@Mixin(TamableAnimal.class)
public abstract class TamableAnimalMixin {

    @Inject(method = "shouldTryTeleportToOwner", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$noOwnerTeleport(CallbackInfoReturnable<Boolean> cir) {
        if (AnchoredPets.isAnchoredPet(this)) {
            cir.setReturnValue(false);
        }
    }
}
