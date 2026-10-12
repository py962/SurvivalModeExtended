package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.PirateRaiderEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Steers vanilla boats carrying a {@link PirateRaiderEntity} toward that pirate's attack
 * target, holding at crossbow range instead of ramming.
 * <p>
 * Boats have no autonomous movement (paddling is player-input driven), so the boat is
 * driven server-side by setting its velocity directly each tick. Velocity comes from the
 * direction vector to the target; the turn-rate-limited yaw is visual only.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class PirateBoatSteeringHandler {

    /** Stop closing at this distance (blocks) and hold range. */
    private static final double STANDOFF_RANGE = 10.0;

    /** Give up chasing beyond this distance (blocks). */
    private static final double MAX_CHASE_RANGE = 40.0;

    private static final double CHASE_SPEED = 0.44; // blocks/tick, ~10% above a paddled boat
    private static final float MAX_TURN_PER_TICK = 6.0F; // degrees

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Boat boat)) continue;

            LivingEntity target = findTarget(boat);
            if (target != null) {
                steerTowards(boat, target);
            }
        }
    }

    /** Prefers whichever of the boat's (up to 2) passengers is a pirate that currently has a live target. */
    private static LivingEntity findTarget(Boat boat) {
        for (Entity passenger : boat.getPassengers()) {
            if (!(passenger instanceof PirateRaiderEntity pirate)) continue;
            LivingEntity target = pirate.getTarget();
            if (target != null && target.isAlive()) return target;
        }
        return null;
    }

    private static void steerTowards(Boat boat, LivingEntity target) {
        double dx = target.getX() - boat.getX();
        double dz = target.getZ() - boat.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > MAX_CHASE_RANGE || distance < 1.0e-4) return;

        // Visual facing only; movement below does not depend on it.
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float currentYaw = boat.getYRot();
        float turn = clampTurn(wrapDegrees(desiredYaw - currentYaw));
        boat.setYRot(currentYaw + turn);

        Vec3 current = boat.getDeltaMovement();
        if (distance <= STANDOFF_RANGE) {
            // Hold position by damping drift so the riders can line up shots.
            boat.setDeltaMovement(current.x * 0.8, current.y, current.z * 0.8);
            return;
        }

        double dirX = dx / distance;
        double dirZ = dz / distance;
        boat.setDeltaMovement(dirX * CHASE_SPEED, current.y, dirZ * CHASE_SPEED);
    }

    private static float clampTurn(float deltaYaw) {
        if (deltaYaw > MAX_TURN_PER_TICK) return MAX_TURN_PER_TICK;
        if (deltaYaw < -MAX_TURN_PER_TICK) return -MAX_TURN_PER_TICK;
        return deltaYaw;
    }

    private static float wrapDegrees(float degrees) {
        degrees = degrees % 360.0F;
        if (degrees >= 180.0F) degrees -= 360.0F;
        if (degrees < -180.0F) degrees += 360.0F;
        return degrees;
    }
}
