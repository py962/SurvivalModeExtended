package com.terrainlab.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Safety while sleeping in a houseboat or howdah: the sleeper is invisible to mobs, the sleeper and
 * the vehicle ignore damage from mobs (projectiles included) and nearby mobs are pushed away. The
 * damage protection lasts a few seconds after waking. Other players and the environment can still
 * do damage.
 */
public final class VehicleSleepGuard {

    public static final int GRACE_TICKS = 60;
    private static final double CLEAR_MARGIN = 2.0;
    private static final double PUSH_SPEED = 0.5;
    private static final double FORGET_RADIUS = 64.0;

    private static final Map<UUID, Long> PROTECTED_UNTIL = new ConcurrentHashMap<>();

    private VehicleSleepGuard() {
    }

    public static void protect(Entity entity) {
        PROTECTED_UNTIL.put(entity.getUUID(), Long.MAX_VALUE);
        if (entity instanceof Player sleeper) {
            for (Mob mob : sleeper.level().getEntitiesOfClass(Mob.class, sleeper.getBoundingBox().inflate(FORGET_RADIUS),
                    m -> m.getTarget() == sleeper)) {
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                mob.setTarget(null);
            }
        }
    }

    /** Asleep aboard a protected vehicle: mobs can't see you. */
    private static boolean isHiddenSleeper(Entity entity) {
        return entity instanceof Player player && player.isSleeping() && isProtected(player);
    }

    public static void startGrace(Entity entity) {
        PROTECTED_UNTIL.put(entity.getUUID(), entity.level().getGameTime() + GRACE_TICKS);
    }

    public static boolean isProtected(Entity entity) {
        Long until = PROTECTED_UNTIL.get(entity.getUUID());
        if (until == null) {
            return false;
        }
        if (entity.level().getGameTime() > until) {
            PROTECTED_UNTIL.remove(entity.getUUID());
            return false;
        }
        return true;
    }

    /** Damage caused by a mob, directly or through its projectile. */
    public static boolean blocks(Entity target, DamageSource source) {
        Entity cause = source.getEntity();
        return cause != null && !(cause instanceof Player) && isProtected(target);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (blocks(event.getEntity(), event.getSource())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (isHiddenSleeper(event.getNewAboutToBeSetTarget())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /** While protected, pushes every non-player mob out of the vehicle and a margin around it. */
    public static void tickVehicle(Entity vehicle) {
        if (isProtected(vehicle)) {
            pushOut(vehicle, vehicle.getBoundingBox().inflate(CLEAR_MARGIN, 2.0, CLEAR_MARGIN), false);
        }
    }

    /** Pushes hostile mobs standing on top of the vehicle off over the nearest edge. */
    public static void clearDeck(Entity vehicle) {
        AABB box = vehicle.getBoundingBox();
        pushOut(vehicle, new AABB(box.minX, box.maxY - 0.3, box.minZ, box.maxX, box.maxY + 2.0, box.maxZ), true);
    }

    private static void pushOut(Entity vehicle, AABB zone, boolean hostileOnly) {
        Entity root = vehicle.getRootVehicle();
        for (LivingEntity mob : vehicle.level().getEntitiesOfClass(LivingEntity.class, zone,
                e -> !(e instanceof Player) && e != vehicle && e.getRootVehicle() != root && e.isAlive())) {
            if (hostileOnly && !(mob instanceof Enemy)) {
                continue;
            }
            double dx = mob.getX() - vehicle.getX();
            double dz = mob.getZ() - vehicle.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-3) {
                double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
                dx = Math.cos(angle);
                dz = Math.sin(angle);
                length = 1.0;
            }
            Vec3 motion = mob.getDeltaMovement();
            mob.setDeltaMovement(dx / length * PUSH_SPEED, Math.max(motion.y, 0.1), dz / length * PUSH_SPEED);
            mob.needsSync = true;
        }
    }
}
