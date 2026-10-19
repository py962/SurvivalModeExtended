package com.terrainlab.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.Horse;

import java.util.EnumSet;

/**
 * Makes a ridden Horse path toward its Mob rider's current attack target.
 * Claims {@link Goal.Flag#MOVE}, so a lower-priority movement goal resumes once
 * the target is lost, dies or leaves range.
 */
public class HorseChaseRiderTargetGoal extends Goal {

    /** Chase is abandoned beyond this distance, in blocks. */
    private static final double MAX_CHASE_RANGE = 40.0;

    /** Ticks between re-paths toward the moving target. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    private static final double SPEED_MODIFIER = 5.0D;

    private final Horse horse;
    private LivingEntity target;
    private int repathCooldown;

    public HorseChaseRiderTargetGoal(Horse horse) {
        this.horse = horse;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = findRiderTarget();
        if (candidate == null) return false;
        if (horse.distanceToSqr(candidate) > MAX_CHASE_RANGE * MAX_CHASE_RANGE) return false;

        target = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive()) return false;
        if (horse.distanceToSqr(target) > MAX_CHASE_RANGE * MAX_CHASE_RANGE) return false;
        return findRiderTarget() == target;
    }

    @Override
    public void start() {
        repathCooldown = 0;
    }

    @Override
    public void tick() {
        if (target == null) return;

        horse.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (--repathCooldown <= 0) {
            repathCooldown = REPATH_INTERVAL_TICKS;
            horse.getNavigation().moveTo(target, SPEED_MODIFIER);
        }
    }

    @Override
    public void stop() {
        target = null;
        horse.getNavigation().stop();
    }

    /** Live attack target of the horse's Mob rider, or null. */
    private LivingEntity findRiderTarget() {
        var passenger = horse.getFirstPassenger();
        if (!(passenger instanceof Mob rider)) return null;

        LivingEntity riderTarget = rider.getTarget();
        if (riderTarget == null || !riderTarget.isAlive()) return null;

        return riderTarget;
    }
}
