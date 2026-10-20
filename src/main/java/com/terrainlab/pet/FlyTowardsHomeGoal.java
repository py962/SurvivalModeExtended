package com.terrainlab.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

/**
 * Flying counterpart of vanilla MoveTowardsRestrictionGoal for the anchored birds. The vanilla
 * goal never fires for flyers: DefaultRandomPos only accepts a position that FlyingPathNavigation
 * calls stable (a solid block) and that has no path malus (a solid block is BLOCKED), so it
 * always returns null. This goal instead flies the bird home in short legs: each leg is at most
 * {@link #LEG_LENGTH} blocks straight towards the home centre, a couple of blocks above the
 * ground there, re-planned whenever the current leg finishes or every {@link #REPLAN_TICKS}.
 * It runs only while the tamed bird is outside its roaming zone and stops as soon as it is back in.
 */
public class FlyTowardsHomeGoal extends Goal {

    private static final double LEG_LENGTH = 16.0;
    private static final int REPLAN_TICKS = 40;
    private static final int MIN_HEIGHT_ABOVE_GROUND = 2;
    private static final int MAX_HEIGHT_ABOVE_GROUND = 16;

    private final TamableAnimal mob;
    private final double speedModifier;
    private int replanTimer;

    public FlyTowardsHomeGoal(TamableAnimal mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return shouldHeadHome();
    }

    @Override
    public boolean canContinueToUse() {
        return shouldHeadHome();
    }

    private boolean shouldHeadHome() {
        return this.mob.isTame()
                && !this.mob.isOrderedToSit()
                && !this.mob.isPassenger()
                && !this.mob.isLeashed()
                && this.mob.hasHome()
                && !this.mob.isWithinHome();
    }

    @Override
    public void start() {
        this.replanTimer = 0;
        flyNextLeg();
    }

    @Override
    public void stop() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (++this.replanTimer >= REPLAN_TICKS || this.mob.getNavigation().isDone()) {
            this.replanTimer = 0;
            flyNextLeg();
        }
    }

    private void flyNextLeg() {
        BlockPos home = this.mob.getHomePosition();
        double homeX = home.getX() + 0.5;
        double homeZ = home.getZ() + 0.5;
        double dx = homeX - this.mob.getX();
        double dz = homeZ - this.mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        double tx;
        double ty;
        double tz;
        if (horizontal <= LEG_LENGTH) {
            // Home is (nearly) straight above or below: aim at the centre itself.
            tx = homeX;
            ty = home.getY() + 1.0;
            tz = homeZ;
        } else {
            tx = this.mob.getX() + dx / horizontal * LEG_LENGTH;
            tz = this.mob.getZ() + dz / horizontal * LEG_LENGTH;
            ty = cruiseHeight(Mth.floor(tx), Mth.floor(tz));
        }

        boolean pathed = this.mob.getNavigation().moveTo(tx, ty, tz, this.speedModifier);
        if (!pathed) {
            // No path inside the search region (e.g. a wall of terrain): fly straight at the leg target.
            this.mob.getMoveControl().setWantedPosition(tx, ty, tz, this.speedModifier);
        }
    }

    /** Keeps the bird's current altitude, clamped to a band above the ground at the leg target. */
    private double cruiseHeight(int x, int z) {
        Level level = this.mob.level();
        if (!level.hasChunkAt(x, z)) {
            return this.mob.getY(); // never force-load a chunk just to read its heightmap
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return Mth.clamp(this.mob.getY(), ground + MIN_HEIGHT_ABOVE_GROUND, ground + MAX_HEIGHT_ABOVE_GROUND);
    }
}
