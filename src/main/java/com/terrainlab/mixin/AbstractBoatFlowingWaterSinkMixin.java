package com.terrainlab.mixin;

import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Caps a boat's sink speed while it is under flowing water, so small drops in a stream do not
 * drag it deep. Vanilla {@code floatBoat()} applies no buoyancy for {@code UNDER_FLOWING_WATER};
 * real waterfalls still eject the boat through vanilla's 60-tick out-of-control timer.
 */
@Mixin(AbstractBoat.class)
public class AbstractBoatFlowingWaterSinkMixin {

    // Most negative Y velocity allowed under flowing water, blocks/tick.
    private static final double MAX_FLOWING_WATER_SINK_SPEED = -0.08;

    @Shadow
    private AbstractBoat.Status status;

    @Inject(method = "floatBoat", at = @At("TAIL"))
    private void terrainlab$limitFlowingWaterSinkSpeed(CallbackInfo ci) {
        if (this.status != AbstractBoat.Status.UNDER_FLOWING_WATER) return;

        AbstractBoat boat = (AbstractBoat) (Object) this;
        Vec3 delta = boat.getDeltaMovement();
        if (delta.y < MAX_FLOWING_WATER_SINK_SPEED) {
            boat.setDeltaMovement(delta.x, MAX_FLOWING_WATER_SINK_SPEED, delta.z);
        }
    }
}
