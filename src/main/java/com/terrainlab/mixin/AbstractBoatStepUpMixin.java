package com.terrainlab.mixin;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snaps a boat onto the higher water surface when it noses into a small upward step, instead of
 * stalling as {@code UNDER_FLOWING_WATER}. Uses vanilla's own landing-snap height formula from
 * {@code floatBoat()} and skips that tick's float physics, as the vanilla snap does.
 */
@Mixin(AbstractBoat.class)
public class AbstractBoatStepUpMixin {

    private static final double UP_TOLERANCE = 1.0; // blocks

    @Shadow
    private AbstractBoat.Status status;

    @Inject(method = "floatBoat", at = @At("HEAD"), cancellable = true)
    private void terrainlab$snapOntoHigherStep(CallbackInfo ci) {
        if (this.status != AbstractBoat.Status.UNDER_FLOWING_WATER) return;

        AbstractBoat boat = (AbstractBoat) (Object) this;

        double targetY = boat.getWaterLevelAbove() - boat.getBbHeight() + 0.101;
        double delta = targetY - boat.getY();

        // Only a small upward step qualifies; deeper submersion is left to the sink cap.
        if (delta <= 0.0 || delta > UP_TOLERANCE) return;

        Vec3 offset = new Vec3(0.0, delta, 0.0);
        if (!boat.level().noCollision(boat, boat.getBoundingBox().move(offset))) return;

        boat.setPos(boat.getX(), targetY, boat.getZ());
        Vec3 currentDelta = boat.getDeltaMovement();
        boat.setDeltaMovement(currentDelta.x, 0.0, currentDelta.z);

        ci.cancel();
    }
}
