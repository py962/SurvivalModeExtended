package com.terrainlab.mixin;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.HouseboatEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes houseboat physics and steering while the boat is suspended from a happy ghast, so its
 * position is driven entirely by the ghast.
 */
@Mixin(AbstractBoat.class)
public class AbstractBoatGravityMixin {

    private static final boolean DEBUG = false;

    @Shadow private boolean inputLeft;
    @Shadow private boolean inputRight;
    @Shadow private boolean inputUp;
    @Shadow private boolean inputDown;

    @Inject(method = "floatBoat", at = @At("HEAD"), cancellable = true)
    private void suppressFloatWhenSuspended(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!(boat instanceof HouseboatEntity houseboat)) return;
        if (!houseboat.isSuspendedFromGhast()) return;

        boat.setDeltaMovement(Vec3.ZERO);
        ci.cancel();
    }

    // 26.x: entity interpolation only runs client-side (Entity#commonTick), so the old server-side
    // interpolate() skip for suspended boats is no longer needed and was removed.

    // Position trace (HEAD / TAIL) for a suspended boat.
    @Inject(method = "tick", at = @At("HEAD"))
    private void traceHead(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!(boat instanceof HouseboatEntity houseboat) || !houseboat.isSuspendedFromGhast()) return;
        if (boat.tickCount % 10 != 0) return;
        // TerrainLab.LOGGER.info("[BOAT-POS] tick={} side={} HEAD y={}",
        //         boat.tickCount, boat.level().isClientSide() ? "CLIENT" : "SERVER", boat.getY());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void traceTail(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!(boat instanceof HouseboatEntity houseboat) || !houseboat.isSuspendedFromGhast()) return;
        if (boat.tickCount % 10 != 0) return;
        // TerrainLab.LOGGER.info("[BOAT-POS] tick={} side={} TAIL y={} isLocalInstanceAuthoritative={}",
        //         boat.tickCount, boat.level().isClientSide() ? "CLIENT" : "SERVER",
        //         boat.getY(), boat.isLocalInstanceAuthoritative());
    }

    // controlBoat() runs on the authoritative side, which is the server while suspended.
    @Inject(method = "controlBoat", at = @At("HEAD"), cancellable = true)
    private void suppressSteeringWhenSuspended(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!(boat instanceof HouseboatEntity houseboat)) return;

        boolean suspended = houseboat.isSuspendedFromGhast();

        if (DEBUG && boat.tickCount % 10 == 0) {
            // TerrainLab.LOGGER.info(
            //         "[BOAT-STEER] tick={} side={} suspended={} inputLeft={} inputRight={} inputUp={} deltaRotationWillApply={}",
            //         boat.tickCount,
            //         boat.level().isClientSide() ? "CLIENT" : "SERVER",
            //         suspended, this.inputLeft, this.inputRight, this.inputUp,
            //         !suspended
            // );
        }

        if (!suspended) return;

        if (boat.isVehicle()) {
            boat.setPaddleState(
                    this.inputRight && !this.inputLeft || this.inputUp,
                    this.inputLeft && !this.inputRight || this.inputUp
            );
        }
        ci.cancel();
    }
}
