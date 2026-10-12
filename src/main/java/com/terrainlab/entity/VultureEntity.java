package com.terrainlab.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import com.terrainlab.registry.ModSounds;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

import java.util.EnumSet;
import java.util.List;

/**
 * Parrot-based scavenger that soars without flapping. Goals, highest priority first: scavenge dropped
 * rotten flesh, circle at altitude by day, and occasionally perch on a local hilltop.
 */
public class VultureEntity extends Parrot {

    private static final boolean DEBUG_LOGGING = false;

    private static final double SCAVENGE_RANGE = 12.0; // blocks

    private static boolean isDayTime(Level level) {
        long dayTime = level.getDayTime() % 24000L;
        return dayTime < 12000L;
    }

    public VultureEntity(EntityType<? extends Parrot> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.getAvailableGoals()
                .removeIf(wrapped -> wrapped.getGoal() instanceof LandOnOwnersShoulderGoal);
        this.goalSelector.addGoal(1, new VultureScavengeGoal(this));
        this.goalSelector.addGoal(2, new VultureCircleGoal(this));
        this.goalSelector.addGoal(3, new VultureHilltopPerchGoal(this));
    }

    @Override
    protected boolean isFlapping() {
        // Keeps the wing animation in its outstretched glide pose.
        return false;
    }

    @Override
    protected void onFlap() {
        // Suppresses Parrot's flap sound; unreachable while isFlapping() is false.
    }

    @Override
    public SoundEvent getAmbientSound() {
        return ModSounds.VULTURE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.VULTURE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.VULTURE_DEATH.get();
    }

    private static class VultureScavengeGoal extends Goal {
        private final VultureEntity vulture;
        private ItemEntity targetItem;

        VultureScavengeGoal(VultureEntity vulture) {
            this.vulture = vulture;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            List<ItemEntity> nearby = this.vulture.level().getEntitiesOfClass(
                    ItemEntity.class, this.vulture.getBoundingBox().inflate(SCAVENGE_RANGE),
                    item -> item.getItem().is(Items.ROTTEN_FLESH));
            if (nearby.isEmpty()) return false;
            this.targetItem = nearby.get(0);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return this.targetItem != null && this.targetItem.isAlive();
        }

        @Override
        public void tick() {
            if (this.targetItem == null) return;
            this.vulture.getNavigation().moveTo(this.targetItem, 1.0);

            if (this.vulture.distanceToSqr(this.targetItem) < 2.0) {
                this.targetItem.getItem().shrink(1);
                if (this.targetItem.getItem().isEmpty()) {
                    this.targetItem.discard();
                }
                // TODO: play a scavenge/peck sound.
            }
        }

        @Override
        public void stop() {
            this.targetItem = null;
        }
    }

    /**
     * Daytime high-altitude circling. The orbit target advances steadily around the circle, always
     * slightly ahead of the vulture, so it glides continuously instead of stopping at waypoints.
     */
    private static class VultureCircleGoal extends Goal {
        private static final int CIRCLE_ALTITUDE = 28;
        private static final int CIRCLE_RADIUS = 10;
        // Short enough that the current target is never reached before the next is issued.
        private static final int WAYPOINT_INTERVAL_TICKS = 10;
        // Radians per waypoint step; a full circle takes ~30 s.
        private static final double ANGULAR_STEP = Math.PI / 30.0;
        private static final int RECENTER_INTERVAL_TICKS = 1200;

        private final VultureEntity vulture;
        private BlockPos center;
        private double angle;
        private double angularDirection;
        private int waypointTimer;
        private int recenterTimer;

        VultureCircleGoal(VultureEntity vulture) {
            this.vulture = vulture;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return isDayTime(this.vulture.level());
        }

        @Override
        public boolean canContinueToUse() {
            return isDayTime(this.vulture.level());
        }

        @Override
        public void start() {
            pickCenter();
            angle = vulture.getRandom().nextDouble() * Math.PI * 2;
            // Turn direction is fixed for the whole circling bout.
            angularDirection = vulture.getRandom().nextBoolean() ? 1.0 : -1.0;
            waypointTimer = 0;
            recenterTimer = 0;
            advanceOrbitPoint();
        }

        @Override
        public void tick() {
            recenterTimer++;
            if (recenterTimer >= RECENTER_INTERVAL_TICKS) {
                pickCenter();
                recenterTimer = 0;
            }

            waypointTimer++;
            if (waypointTimer >= WAYPOINT_INTERVAL_TICKS) {
                waypointTimer = 0;
                advanceOrbitPoint();
            }
        }

        private void pickCenter() {
            BlockPos ground = vulture.blockPosition();
            int y = Math.min(ground.getY() + CIRCLE_ALTITUDE, vulture.level().getMaxY() - 5);
            center = new BlockPos(ground.getX(), y, ground.getZ());
        }

        private void advanceOrbitPoint() {
            angle += ANGULAR_STEP * angularDirection;
            double x = center.getX() + Math.cos(angle) * CIRCLE_RADIUS;
            double z = center.getZ() + Math.sin(angle) * CIRCLE_RADIUS;
            vulture.getNavigation().moveTo(x, center.getY(), z, 1.0);
        }
    }

    private static class VultureHilltopPerchGoal extends Goal {
        private static final int SEARCH_RADIUS = 12;
        private static final int NEIGHBOR_OFFSET = 5;
        private static final int HEIGHT_MARGIN = 3;
        private static final int CANDIDATES_TO_TRY = 6;
        private static final int PERCH_COOLDOWN_TICKS = 300;
        private static final double ARRIVAL_DISTANCE_SQR = 2.25; // 1.5 blocks from the standing spot
        private static final int GIVE_UP_TICKS = 200; // goal tick() calls before abandoning the perch

        private final VultureEntity vulture;
        private BlockPos perchTarget;
        private int cooldown;
        private int travelTicks;

        VultureHilltopPerchGoal(VultureEntity vulture) {
            this.vulture = vulture;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            if (this.vulture.getRandom().nextInt(150) != 0) return false;

            this.perchTarget = findNearbyHilltop();
            return this.perchTarget != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (this.perchTarget == null
                    || this.travelTicks > GIVE_UP_TICKS
                    || this.vulture.getNavigation().isDone()) {
                return false;
            }
            // perchTarget is the top solid block; the vulture stands on the block above it.
            BlockPos standPos = this.perchTarget.above();
            return this.vulture.distanceToSqr(
                    standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5) > ARRIVAL_DISTANCE_SQR;
        }

        @Override
        public void tick() {
            this.travelTicks++;
        }

        @Override
        public void start() {
            this.travelTicks = 0;
            if (this.perchTarget != null) {
                this.vulture.getNavigation().moveTo(
                        this.perchTarget.getX() + 0.5, this.perchTarget.getY() + 1.0, this.perchTarget.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            this.cooldown = PERCH_COOLDOWN_TICKS;
            this.perchTarget = null;
        }

        private BlockPos findNearbyHilltop() {
            Level level = this.vulture.level();
            BlockPos origin = this.vulture.blockPosition();

            for (int i = 0; i < CANDIDATES_TO_TRY; i++) {
                int dx = this.vulture.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
                int dz = this.vulture.getRandom().nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
                int cx = origin.getX() + dx;
                int cz = origin.getZ() + dz;

                int candidateHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
                BlockPos candidate = new BlockPos(cx, candidateHeight, cz);

                if (!level.canSeeSky(candidate.above())) continue;

                int higherThanCount = 0;
                int[][] offsets = {{NEIGHBOR_OFFSET, 0}, {-NEIGHBOR_OFFSET, 0}, {0, NEIGHBOR_OFFSET}, {0, -NEIGHBOR_OFFSET}};
                for (int[] offset : offsets) {
                    int nx = cx + offset[0];
                    int nz = cz + offset[1];
                    int neighborHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz) - 1;
                    if (candidateHeight >= neighborHeight + HEIGHT_MARGIN) {
                        higherThanCount++;
                    }
                }

                if (higherThanCount >= 3) {
                    return candidate;
                }
            }
            return null;
        }
    }

    public static boolean checkVultureSpawnRules(net.minecraft.world.entity.EntityType<VultureEntity> type,
                                                 net.minecraft.world.level.LevelAccessor level,
                                                 net.minecraft.world.entity.EntitySpawnReason reason,
                                                 BlockPos pos,
                                                 net.minecraft.util.RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        boolean onLand = below.is(BlockTags.PARROTS_SPAWNABLE_ON);
        boolean onTerracotta = below.is(BlockTags.TERRACOTTA);
        boolean onSand = below.is(net.minecraft.world.level.block.Blocks.SAND)
                || below.is(net.minecraft.world.level.block.Blocks.RED_SAND);
        boolean result = onLand || onTerracotta || onSand;
        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            //         "[VULTURE_SPAWN] check at {}: belowBlock={}, onLand={}, onTerracotta={}, onSand={}, result={}, reason={}",
            //         pos, below.getBlock(), onLand, onTerracotta, onSand, result, reason);
        }

        return result;
    }
}
