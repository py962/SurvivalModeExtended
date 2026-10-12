package com.terrainlab.mixin;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.HouseboatEntity;
import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Happy Ghast changes: Agility on the harness speeds up riding, and a ghast leashed to a houseboat hovers
 * above it and tows it, flown either by a direct rider or by the boat's seated pilot.
 */
@Mixin(HappyGhast.class)
public class HappyGhastMixin {

    @Unique private static final boolean DEBUG = false;

    @Unique private static final double HOVER_HEIGHT = 6.0; // blocks above the boat
    // Blocks per tick an unpiloted towing ghast sinks (4 blocks/second).
    @Unique private static final double DRIFT_SPEED = 0.2;
    // Ground/liquid probe depth below the boat; must be >= DRIFT_SPEED so a short drift still counts as landed.
    @Unique private static final double REST_PROBE = 0.25;
    @Unique private static final float MOVE_SPEED = 0.5f;
    @Unique private static final float FORWARD_SPEED = 1.2f;
    @Unique private static final float TURN_SPEED = 3.0f;
    @Unique private static final int STILL_REFRESH_TICKS = 10;
    @Unique private boolean terrainlab$positionedAboveBoat = false;
    // Set when towing turned NoAI on, so only that NoAI is cleared afterwards; saved with the entity.
    @Unique private boolean terrainlab$towingSetNoAi = false;
    @Unique private static final String TOWING_NO_AI_KEY = "terrainlab_towing_no_ai";

    @Shadow
    private void setServerStillTimeout(int i) {
        throw new AssertionError();
    }

    // Agility on the harness (BODY slot): +1/3 movement speed per level for riding and towing; turning is unaffected.
    @Unique
    private float terrainlab$agilityScale() {
        HappyGhast ghast = (HappyGhast) (Object) this;
        ItemStack harness = ghast.getItemBySlot(EquipmentSlot.BODY);
        Holder<Enchantment> agility =
                ModEnchantments.holder(ghast.registryAccess(), ModEnchantments.AGILITY);
        int level = EnchantmentHelper.getItemEnchantmentLevel(agility, harness);
        return 1.0f + (level / 3.0f);
    }

    @Unique
    private HouseboatEntity terrainlab$getLeashedBoat() {
        HappyGhast ghast = (HappyGhast) (Object) this;
        List<Leashable> leashed = Leashable.leashableLeashedTo(ghast);
        for (Leashable l : leashed) {
            if (l instanceof HouseboatEntity houseboat) return houseboat;
        }
        return null;
    }

    // Whether the boat is resting on ground or in/on liquid; recomputed every tick.
    @Unique
    private static boolean terrainlab$isBoatResting(HouseboatEntity boat) {
        if (boat.isInWater() || boat.isInLava()) return true;
        AABB below = boat.getBoundingBox().move(0.0, -REST_PROBE, 0.0);
        return !boat.level().noCollision(boat, below) || boat.level().containsAnyLiquid(below);
    }

    // Collision-clamped downward move (dy < 0) so a descent lands exactly on the ground; returns a value in [dy, 0].
    @Unique
    private static double terrainlab$clearDrop(HouseboatEntity boat, double dx, double dy, double dz) {
        // Checked at the boat's next horizontal position.
        Vec3 allowed = net.minecraft.world.entity.Entity.collideBoundingBox(
                boat, new Vec3(0.0, dy, 0.0), boat.getBoundingBox().move(dx, 0.0, dz), boat.level(), List.of());
        return Math.min(0.0, Math.max(dy, allowed.y));
    }

    @Unique
    private static void terrainlab$updateResting(HouseboatEntity boat) {
        boolean resting = terrainlab$isBoatResting(boat);
        boat.terrainlab$setRestingOnSurface(resting);
        boat.setOnGround(resting && !boat.isInWater());
    }

    // Block- and world-border-clamped version of the given move for the entity's current box.
    @Unique
    private static Vec3 terrainlab$collide(net.minecraft.world.entity.Entity entity, Vec3 move) {
        return net.minecraft.world.entity.Entity.collideBoundingBox(
                entity, move, entity.getBoundingBox(), entity.level(), List.of());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void terrainlab$saveTowingNoAi(ValueOutput output, CallbackInfo ci) {
        if (terrainlab$towingSetNoAi) {
            output.putBoolean(TOWING_NO_AI_KEY, true);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void terrainlab$loadTowingNoAi(ValueInput input, CallbackInfo ci) {
        terrainlab$towingSetNoAi = input.getBooleanOr(TOWING_NO_AI_KEY, false);
    }

    // A direct rider on the ghast takes priority over a player seated in the boat; requires a harness.
    @Unique
    private Player terrainlab$getControllingPlayer(HouseboatEntity boat) {
        HappyGhast ghast = (HappyGhast) (Object) this;
        if (!ghast.isWearingBodyArmor()) return null;

        if (ghast.getFirstPassenger() instanceof Player directRider) {
            return directRider;
        }

        for (net.minecraft.world.entity.Entity passenger : boat.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    // Agility for direct riding. Scales vanilla's ridden input on both sides, since the rider's client
    // simulates a player-controlled vehicle.
    @Inject(method = "getRiddenInput", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$agilityRiddenInput(Player player, Vec3 input,
                                               CallbackInfoReturnable<Vec3> cir) {
        float scale = terrainlab$agilityScale();
        if (scale != 1.0f) {
            cir.setReturnValue(cir.getReturnValue().scale(scale));
        }
    }

    // While towing, manageGhastPosition moves the ghast; vanilla travel is cancelled for every control path.
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void suppressTravelWhenTowing(Vec3 travelVector, CallbackInfo ci) {
        HappyGhast ghast = (HappyGhast) (Object) this;
        if (ghast.level().isClientSide()) return;
        if (terrainlab$getLeashedBoat() == null) return;
        ghast.setDeltaMovement(Vec3.ZERO);
        ci.cancel();
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void suppressAiStepWhenTowing(CallbackInfo ci) {
        HappyGhast ghast = (HappyGhast) (Object) this;
        if (ghast.level().isClientSide()) return;
        if (terrainlab$getLeashedBoat() == null) return;
        ghast.setDeltaMovement(Vec3.ZERO);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void manageGhastPosition(CallbackInfo ci) {
        HappyGhast ghast = (HappyGhast) (Object) this;
        if (ghast.level().isClientSide()) return;

        HouseboatEntity boat = terrainlab$getLeashedBoat();
        if (boat == null) {
            if (terrainlab$towingSetNoAi) {
                ghast.setNoAi(false);
                terrainlab$towingSetNoAi = false;
            }
            terrainlab$positionedAboveBoat = false;
            return;
        }

        boolean debugTick = DEBUG && ghast.tickCount % 10 == 0;

        if (!ghast.isNoAi()) {
            ghast.setNoAi(true);
            terrainlab$towingSetNoAi = true;
        }

        ghast.getMoveControl().setWait();
        ghast.getNavigation().stop();

        if (!terrainlab$positionedAboveBoat) {
            ghast.snapTo(boat.getX(), boat.getY() + HOVER_HEIGHT, boat.getZ(),
                    boat.getYRot(), 0.0f);
            ghast.setYRot(boat.getYRot());
            ghast.yHeadRot = boat.getYRot();
            ghast.yBodyRot = boat.getYRot();
            ghast.setDeltaMovement(Vec3.ZERO);
            terrainlab$positionedAboveBoat = true;
        }

        Player pilot = terrainlab$getControllingPlayer(boat);
        boolean directRide = pilot != null && ghast.getFirstPassenger() == pilot;

        if (debugTick) {
            // TerrainLab.LOGGER.info(
            //         "[GHAST-TOW] tick={} pilot={} directRide={} isOnStillTimeout={} isNoAi={} isWearingBodyArmor={} boatIsInWater={} boatY={} ghastY={} waterLevel={} boatOnGround={} boatIsInAir={}",
            //         ghast.tickCount,
            //         pilot != null ? pilot.getScoreboardName() : "null",
            //         directRide,
            //         ghast.isOnStillTimeout(),
            //         ghast.isNoAi(),
            //         ghast.isWearingBodyArmor(),
            //         boat.isInWater(),
            //         boat.getY(),
            //         ghast.getY(),
            //         ((AbstractBoatAccessor) boat).getWaterLevel(),
            //         boat.onGround(),
            //         boat.isInAir()
            // );
        }

        if (pilot == null) {
            this.setServerStillTimeout(STILL_REFRESH_TICKS);

            if (debugTick) {
                // TerrainLab.LOGGER.info("[GHAST-TOW] tick={} FROZEN branch — no pilot detected", ghast.tickCount);
            }

            ghast.setDeltaMovement(Vec3.ZERO);

            // Unpiloted: sink until the boat rests on ground or liquid, stopping above a liquid surface.
            double drift = 0.0;
            if (!terrainlab$isBoatResting(boat)) {
                drift = terrainlab$clearDrop(boat, 0.0, -DRIFT_SPEED, 0.0);
                if (drift < 0.0 && boat.level().containsAnyLiquid(boat.getBoundingBox().move(0.0, drift, 0.0))) {
                    drift = 0.0;
                }
            }

            double tx = boat.getX(), ty = boat.getY() + drift + HOVER_HEIGHT, tz = boat.getZ();
            if (ghast.distanceToSqr(tx, ty, tz) > 0.01) {
                ghast.snapTo(tx, ty, tz, boat.getYRot(), 0.0f);
                ghast.setYRot(boat.getYRot());
                ghast.yHeadRot = boat.getYRot();
                ghast.yBodyRot = boat.getYRot();
            }
            if (!boat.isInWater()) {
                boat.snapTo(ghast.getX(), ghast.getY() - HOVER_HEIGHT, ghast.getZ(),
                        ghast.getYRot(), 0.0f);
            }
            boat.setDeltaMovement(Vec3.ZERO);
            terrainlab$updateResting(boat);
            return;
        }

        if (directRide) {
            // Keeps the ghast server-driven: while still, getControllingPassenger() is not the rider on either
            // side, so the rider's client neither simulates flight nor sends vehicle moves.
            this.setServerStillTimeout(STILL_REFRESH_TICKS);
        }

        float forward = pilot.zza;
        // Turn signal: a direct rider's strafe input, or the boat pilot's paddle state.
        float strafe = directRide
                ? pilot.xxa
                : (boat.getPaddleState(1) ? 1.0f : (boat.getPaddleState(0) ? -1.0f : 0.0f));
        float vertical = pilot.isJumping() ? 1.0f
                : (com.terrainlab.util.ShiftStateCache.isHeld(pilot.getUUID()) ? -1.0f : 0.0f);

        boolean inputActive = forward != 0.0f || strafe != 0.0f || vertical != 0.0f;
        if (DEBUG && (debugTick || inputActive)) {
            // TerrainLab.LOGGER.info(
            //         "[GHAST-INPUT] tick={} directRide={} rawZza={} rawXxa={} paddleLeft={} paddleRight={} isJumping={} isShiftKeyDown={} -> forward={} strafe={} vertical={}",
            //         ghast.tickCount, directRide, pilot.zza, pilot.xxa,
            //         boat.getPaddleState(0), boat.getPaddleState(1),
            //         pilot.isJumping(), pilot.isShiftKeyDown(),
            //         forward, strafe, vertical
            // );
        }

        float newYaw = ghast.getYRot() - strafe * TURN_SPEED;
        ghast.setYRot(newYaw);
        ghast.yHeadRot = newYaw;
        ghast.yBodyRot = newYaw;

        float yawRad = newYaw * (float) (Math.PI / 180.0);
        float agilityScale = terrainlab$agilityScale();
        double dx = -Math.sin(yawRad) * forward * FORWARD_SPEED * agilityScale;
        double dz = Math.cos(yawRad) * forward * FORWARD_SPEED * agilityScale;
        double dy = vertical * MOVE_SPEED * agilityScale;

        double waterLevel = ((AbstractBoatAccessor) boat).getWaterLevel();
        boolean nearWater = waterLevel > -1.0E307; // AbstractBoat uses -Double.MAX_VALUE for "no water"

        if (dy < 0) {
            dy = terrainlab$clearDrop(boat, dx, dy, dz);
            // Stop sinking once the boat reaches the water surface (the boat is clamped to waterLevel below).
            if (nearWater) {
                double toSurface = waterLevel + HOVER_HEIGHT - ghast.getY();
                dy = Math.min(0.0, Math.max(dy, toSurface));
            }
        }

        // Clamp the move against blocks for both boat and ghast; the final boat pass guarantees the boat's box.
        Vec3 move = terrainlab$collide(boat, new Vec3(dx, dy, dz));
        move = terrainlab$collide(ghast, move);
        move = terrainlab$collide(boat, move);
        dx = move.x;
        dy = move.y;
        dz = move.z;

        double newX = ghast.getX() + dx;
        double newY = ghast.getY() + dy;
        double newZ = ghast.getZ() + dz;

        if (DEBUG && (debugTick || inputActive)) {
            // TerrainLab.LOGGER.info(
            //         "[GHAST-MOVE] tick={} newYaw={} yawRad={} dx={} dz={} dy={} ghastPosBefore=({},{},{}) newPos=({},{},{})",
            //         ghast.tickCount, newYaw, yawRad, dx, dz, dy,
            //         ghast.getX(), ghast.getY(), ghast.getZ(),
            //         newX, newY, newZ
            // );
        }

        ghast.snapTo(newX, newY, newZ, newYaw, 0.0f);
        ghast.setDeltaMovement(Vec3.ZERO);

        double boatY = Math.max(newY - HOVER_HEIGHT, waterLevel);
        boat.snapTo(newX, boatY, newZ, newYaw, 0.0f);
        boat.setDeltaMovement(Vec3.ZERO);
        terrainlab$updateResting(boat);

        if (debugTick) {
            // TerrainLab.LOGGER.info(
            //         "[GHAST-TOW] tick={} PILOTED branch pilot={} directRide={} forward={} strafe={} vertical={} dy={} newY={} boatY={}",
            //         ghast.tickCount, pilot.getScoreboardName(), directRide,
            //         forward, strafe, vertical, dy, newY, boatY
            // );
        }
    }
}
