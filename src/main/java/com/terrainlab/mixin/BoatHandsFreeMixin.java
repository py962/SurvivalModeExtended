package com.terrainlab.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only. Vanilla marks the player's hands busy while any movement key is held in a boat, which
 * blocks starting to use an item (drawing a bow or crossbow, throwing a trident, eating, drinking).
 * This clears the flag after {@code LocalPlayer#rideTick} whenever the vehicle is any boat (including
 * the houseboat), so items can be used while paddling. Boat steering input is untouched.
 */
@Mixin(LocalPlayer.class)
public abstract class BoatHandsFreeMixin {

    @Shadow private boolean handsBusy;

    @Inject(method = "rideTick", at = @At("TAIL"))
    private void terrainlab$freeHandsInBoat(CallbackInfo ci) {
        if (((LocalPlayer) (Object) this).getVehicle() instanceof AbstractBoat) {
            this.handsBusy = false;
        }
    }
}
