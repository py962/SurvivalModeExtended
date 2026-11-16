package com.terrainlab.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Blindness and nausea on mobs. A blind mob loses its AI and stumbles about in random straight lines,
 * without pathfinding, so it can walk off ledges or into lava; a nauseous hostile
 * or neutral mob attacks the nearest living thing, players included. Blindness wins over nausea.
 * The goal and target selectors are paused by MobEffectAiMixin, brains by BrainBlindMixin.
 */
public final class MobEffectAiHandler {

    private static final int HEADING_MIN_TICKS = 40;
    private static final int HEADING_EXTRA_TICKS = 60;
    private static final int RETARGET_INTERVAL = 10;
    private static final double MAX_SEARCH = 32.0;

    /** Mobs whose target was forced by nausea, so it can be dropped when the effect ends. */
    private static final Map<Mob, Boolean> FORCED = new WeakHashMap<>();
    /** Current stumbling heading per blind mob: {dx, dz, tick to change}. */
    private static final Map<Mob, double[]> HEADINGS = new WeakHashMap<>();

    private MobEffectAiHandler() {
    }

    public static boolean isExempt(Mob mob) {
        return mob instanceof Warden || mob instanceof WitherBoss || mob instanceof EnderDragon;
    }

    public static boolean isBlind(Mob mob) {
        return mob.hasEffect(MobEffects.BLINDNESS) && !isExempt(mob);
    }

    public static boolean isFrenzied(Mob mob) {
        return (mob instanceof Enemy || mob instanceof NeutralMob)
                && mob.hasEffect(MobEffects.NAUSEA)
                && !mob.hasEffect(MobEffects.BLINDNESS)
                && !isExempt(mob);
    }

    @SubscribeEvent
    public static void onMobTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (isBlind(mob)) {
            FORCED.remove(mob);
            tickBlind(mob);
            return;
        }
        HEADINGS.remove(mob);
        if (isFrenzied(mob)) {
            if ((mob.tickCount + mob.getId()) % RETARGET_INTERVAL == 0) {
                retarget(level, mob);
            }
        } else if (FORCED.remove(mob) != null) {
            dropTarget(mob);
        }
    }

    private static void tickBlind(Mob mob) {
        Brain<?> brain = mob.getBrain();
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
        if (!mob.getNavigation().isDone()) {
            mob.getNavigation().stop();
        }
        double[] heading = HEADINGS.get(mob);
        if (heading == null || mob.tickCount >= heading[2] || mob.horizontalCollision) {
            heading = new double[3];
            if (mob.getRandom().nextInt(3) != 0) {
                double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
                heading[0] = Math.cos(angle);
                heading[1] = Math.sin(angle);
            }
            heading[2] = mob.tickCount + HEADING_MIN_TICKS + mob.getRandom().nextInt(HEADING_EXTRA_TICKS);
            HEADINGS.put(mob, heading);
        }
        if (heading[0] != 0.0 || heading[1] != 0.0) {
            // Straight at a point just ahead: no pathfinding, so nothing steers it from drops.
            mob.getMoveControl().setWantedPosition(
                    mob.getX() + heading[0] * 2.0, mob.getY(), mob.getZ() + heading[1] * 2.0, 0.8);
        }
    }

    private static void retarget(ServerLevel level, Mob mob) {
        double range = MAX_SEARCH;
        if (mob.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE)) {
            range = Math.min(MAX_SEARCH, Math.max(16.0, mob.getAttributeValue(Attributes.FOLLOW_RANGE)));
        }
        LivingEntity nearest = null;
        double best = range * range;
        for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(range),
                e -> e != mob && e.isAlive()
                        && (e instanceof Mob || e instanceof Player player && !player.isCreative() && !player.isSpectator()))) {
            double distance = mob.distanceToSqr(other);
            if (distance < best && mob.getSensing().hasLineOfSight(other)) {
                best = distance;
                nearest = other;
            }
        }
        if (nearest == null) {
            return;
        }
        if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, nearest);
        }
        if (mob.getTarget() != nearest) {
            mob.setTarget(nearest);
        }
        FORCED.put(mob, Boolean.TRUE);
    }

    private static void dropTarget(Mob mob) {
        mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
        } else if (mob.getTarget() != null) {
            mob.setTarget(null);
        }
    }
}
