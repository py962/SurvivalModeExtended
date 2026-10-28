package com.terrainlab.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Goals for wolves sent to sleep on a carpet (WolfCarpetSleepHandler decides who goes where and when
 * they wake). Both hold MOVE and LOOK, so roaming, walk-back-home, stroll, beg and look-around goals
 * stand down. Walking deliberately leaves JUMP free for FloatGoal.
 */
public final class WolfCarpetSleepGoals {

    /** Priority of {@link Sleep}: above everything, so nothing moves a lying wolf. */
    public static final int SLEEP_PRIORITY = 0;
    /** Priority of {@link WalkToCarpet}: above avoid/leap/attack/roaming/stroll goals. */
    public static final int WALK_PRIORITY = 2;

    private static final int GIVE_UP_TICKS = 600; // 30 s without arriving
    private static final int MAX_PATH_FAILURES = 3;
    private static final double ARRIVE_DIST_SQ = 0.5 * 0.5;

    private WolfCarpetSleepGoals() {}

    static Vec3 centre(BlockPos carpet) {
        return new Vec3(carpet.getX() + 0.5, carpet.getY(), carpet.getZ() + 0.5);
    }

    /** Horizontal squared distance from the wolf to the carpet centre. */
    public static double horizontalDistSq(Wolf wolf, BlockPos carpet) {
        double dx = wolf.getX() - (carpet.getX() + 0.5);
        double dz = wolf.getZ() - (carpet.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    /** Walks to the claimed carpet and lies down on arrival; gives up after a while or if unreachable. */
    public static final class WalkToCarpet extends Goal {
        private final Wolf wolf;
        private int ticks;
        private int pathFailures;

        public WalkToCarpet(Wolf wolf) {
            this.wolf = wolf;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return WolfCarpetSleep.hasCarpet(wolf) && !WolfCarpetSleep.isAsleep(wolf);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            ticks = 0;
            pathFailures = 0;
            WolfCarpetSleep.carpet(wolf).ifPresent(this::moveTo);
        }

        @Override
        public void stop() {
            wolf.getNavigation().stop();
        }

        @Override
        public void tick() {
            Optional<BlockPos> claimed = WolfCarpetSleep.carpet(wolf);
            if (claimed.isEmpty()) return;
            BlockPos carpet = claimed.get();

            if (horizontalDistSq(wolf, carpet) <= ARRIVE_DIST_SQ && Math.abs(wolf.getY() - carpet.getY()) < 1.0) {
                wolf.getNavigation().stop();
                wolf.setPos(carpet.getX() + 0.5, wolf.getY(), carpet.getZ() + 0.5);
                wolf.setDeltaMovement(0.0, Math.min(wolf.getDeltaMovement().y, 0.0), 0.0);
                WolfCarpetSleep.lieDown(wolf);
                return;
            }

            if (++ticks > GIVE_UP_TICKS) {
                WolfCarpetSleep.wake(wolf);
                return;
            }
            wolf.getLookControl().setLookAt(carpet.getX() + 0.5, carpet.getY() + 0.5, carpet.getZ() + 0.5);
            if (ticks % 10 == 0 && wolf.getNavigation().isDone()) {
                moveTo(carpet);
            }
        }

        private void moveTo(BlockPos carpet) {
            Vec3 c = centre(carpet);
            if (!wolf.getNavigation().moveTo(c.x, c.y, c.z, 1.0) && ++pathFailures >= MAX_PATH_FAILURES) {
                WolfCarpetSleep.wake(wolf);
            }
        }
    }

    /** Holds a lying wolf on its carpet: no movement, no looking around, no jumping, no begging. */
    public static final class Sleep extends Goal {
        private final Wolf wolf;

        public Sleep(Wolf wolf) {
            this.wolf = wolf;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return WolfCarpetSleep.isAsleep(wolf);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            wolf.getNavigation().stop();
            wolf.setIsInterested(false);
        }

        @Override
        public void tick() {
            wolf.getNavigation().stop();
            wolf.setIsInterested(false);
            Optional<BlockPos> claimed = WolfCarpetSleep.carpet(wolf);
            if (claimed.isPresent()) {
                BlockPos carpet = claimed.get();
                double d = horizontalDistSq(wolf, carpet);
                // Small nudges (other mobs bumping it) are undone; a real displacement wakes it (handler).
                if (d > 0.0025 && d < 1.0) {
                    wolf.setPos(carpet.getX() + 0.5, wolf.getY(), carpet.getZ() + 0.5);
                }
            }
            wolf.setDeltaMovement(0.0, Math.min(wolf.getDeltaMovement().y, 0.0), 0.0);
            float yaw = wolf.getYRot();
            wolf.setYBodyRot(yaw);
            wolf.setYHeadRot(yaw);
            wolf.setXRot(0.0F);
        }
    }
}
