package com.terrainlab.hearth;

import com.terrainlab.mixin.PhantomAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/** Sends repelled hostiles back out of a protecting hearth's range. */
public final class AvoidHearthGoal {

    private AvoidHearthGoal() {
    }

    private static final int CHECK_INTERVAL = 10;

    public static final class Walking extends Goal {
        private final PathfinderMob mob;
        private @Nullable Path path;
        private int cooldown;

        public Walking(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--cooldown > 0 || !(mob.level() instanceof ServerLevel level)) return false;
            cooldown = CHECK_INTERVAL;
            BlockPos hearth = HearthRepel.find(level, mob.blockPosition());
            if (hearth == null) return false;
            Vec3 from = Vec3.atBottomCenterOf(hearth);
            Vec3 away = DefaultRandomPos.getPosAway(mob, HearthRepel.RADIUS + 4, 7, from);
            if (away == null || away.distanceToSqr(from) <= mob.distanceToSqr(from)) return false;
            path = mob.getNavigation().createPath(away.x, away.y, away.z, 0);
            return path != null;
        }

        @Override
        public void start() {
            mob.getNavigation().moveTo(path, 1.2);
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone();
        }

        @Override
        public void stop() {
            path = null;
        }
    }

    /** Phantoms steer by an anchor point rather than a path, so move the anchor outside the range. */
    public static final class Flying extends Goal {
        private static final double CLEAR_SQ = (HearthRepel.RADIUS + 4) * (HearthRepel.RADIUS + 4);

        private final Phantom phantom;
        private Vec3 hearthCentre = Vec3.ZERO;
        private Vec3 retreat = Vec3.ZERO;
        private int cooldown;

        public Flying(Phantom phantom) {
            this.phantom = phantom;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (--cooldown > 0 || !(phantom.level() instanceof ServerLevel level)) return false;
            cooldown = CHECK_INTERVAL;
            BlockPos hearth = HearthRepel.find(level, phantom.blockPosition());
            if (hearth == null) return false;
            hearthCentre = Vec3.atCenterOf(hearth);
            Vec3 dir = new Vec3(phantom.getX() - hearthCentre.x, 0, phantom.getZ() - hearthCentre.z);
            if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(1, 0, 0);
            retreat = hearthCentre.add(dir.normalize().scale(HearthRepel.RADIUS + 12)).add(0, 16, 0);
            return true;
        }

        @Override
        public void start() {
            phantom.setTarget(null);
            ((PhantomAccessor) phantom).terrainlab$setAnchorPoint(BlockPos.containing(retreat));
            ((PhantomAccessor) phantom).terrainlab$setMoveTargetPoint(retreat);
        }

        @Override
        public void tick() {
            ((PhantomAccessor) phantom).terrainlab$setMoveTargetPoint(retreat);
        }

        @Override
        public boolean canContinueToUse() {
            return phantom.position().distanceToSqr(hearthCentre) < CLEAR_SQ;
        }
    }
}
