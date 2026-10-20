package com.terrainlab.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import com.terrainlab.pet.AnchoredPets;
import com.terrainlab.registry.ModSounds;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;

import java.util.EnumSet;
import java.util.List;

/**
 * Goose: a Parrot-based water bird that never perches on shoulders. Goals, by
 * priority: hiss-chase nearby players, return to water when far from it, rare
 * flock migration to a distant body of water, and short rests on water.
 *
 * <p>Tamed geese are bed-anchored pets (see {@link AnchoredPets}): they never migrate, only pick
 * water inside their roaming zone, and none of these goals move them while they sit or while
 * they are outside the zone (FlyTowardsHomeGoal brings them back first).
 */
public class GooseEntity extends Parrot {

    private static final float FLAP_FACTOR = 0.6F;
    private float gooseNextFlap = 1.0F;

    private static final double HISS_RANGE = 6.0;

    public GooseEntity(EntityType<? extends Parrot> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Tamed birds never ride the owner's shoulder (the shoulder layer only draws parrots); they
        // follow and perch nearby instead.
        this.goalSelector.removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal);
        this.goalSelector.getAvailableGoals()
                .removeIf(wrapped -> wrapped.getGoal() instanceof LandOnOwnersShoulderGoal);
        this.goalSelector.addGoal(1, new GooseHissGoal(this));
        this.goalSelector.addGoal(2, new GooseWaterAffinityGoal(this));
        this.goalSelector.addGoal(3, new GooseFlockMigrationGoal(this));
        this.goalSelector.addGoal(4, new GooseWaterRestGoal(this));
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.gooseNextFlap;
    }

    @Override
    protected void onFlap() {
        // Silent flap (no parrot sound). TODO: add a goose wing-flap sound.
        this.gooseNextFlap = this.flyDist + this.flapSpeed / 2.0F * FLAP_FACTOR;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return ModSounds.GOOSE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.GOOSE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.GOOSE_DEATH.get();
    }

    private static class GooseHissGoal extends Goal {
        private final GooseEntity goose;
        private Player target;

        GooseHissGoal(GooseEntity goose) {
            this.goose = goose;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!AnchoredPets.mayWander(this.goose)) return false;
            List<Player> nearby = this.goose.level().getEntitiesOfClass(
                    Player.class, this.goose.getBoundingBox().inflate(HISS_RANGE));
            for (Player player : nearby) {
                if (!player.isSpectator() && !player.isCreative()) {
                    this.target = player;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return this.target != null && this.target.isAlive()
                    && AnchoredPets.mayWander(this.goose)
                    && this.goose.distanceToSqr(this.target) <= HISS_RANGE * HISS_RANGE * 2;
        }

        @Override
        public void tick() {
            if (this.target == null) return;
            this.goose.getLookControl().setLookAt(this.target, 30.0F, 30.0F);
            this.goose.getNavigation().moveTo(this.target, 1.2);
            // Chase and loom only; geese never damage players.
        }

        @Override
        public void stop() {
            this.target = null;
        }
    }

    /**
     * Keeps geese on or near water: does nothing within NEARBY_CHECK_RADIUS of
     * water, otherwise navigates to the closest water found in a wider search.
     */
    private static class GooseWaterAffinityGoal extends Goal {
        private static final int NEARBY_CHECK_RADIUS = 4;
        private static final int WIDE_SEARCH_RADIUS = 20;
        private static final int RECHECK_INTERVAL_TICKS = 100;
        private static final int FAILED_SEARCH_COOLDOWN_TICKS = 200; // the wide scan is ~22k block reads

        private long nextSearchTime;

        private final GooseEntity goose;
        private BlockPos waterTarget;
        private int recheckTimer;

        GooseWaterAffinityGoal(GooseEntity goose) {
            this.goose = goose;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!AnchoredPets.mayWander(goose)) return false;
            long now = goose.level().getGameTime();
            if (now < nextSearchTime) return false;
            nextSearchTime = now + FAILED_SEARCH_COOLDOWN_TICKS / 4;
            if (isNearWater(NEARBY_CHECK_RADIUS)) return false;

            waterTarget = findWater(WIDE_SEARCH_RADIUS);
            if (waterTarget == null) {
                nextSearchTime = now + FAILED_SEARCH_COOLDOWN_TICKS;
            }
            return waterTarget != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (!AnchoredPets.mayWander(goose)) return false;
            if (isNearWater(NEARBY_CHECK_RADIUS)) return false;

            recheckTimer++;
            if (recheckTimer >= RECHECK_INTERVAL_TICKS) {
                recheckTimer = 0;
                waterTarget = findWater(WIDE_SEARCH_RADIUS);
            }
            return waterTarget != null;
        }

        @Override
        public void start() {
            recheckTimer = 0;
            navigateToTarget();
        }

        @Override
        public void tick() {
            if (goose.getNavigation().isDone() && waterTarget != null) {
                navigateToTarget();
            }
        }

        private void navigateToTarget() {
            if (waterTarget != null) {
                goose.getNavigation().moveTo(
                        waterTarget.getX() + 0.5, waterTarget.getY() + 1.0, waterTarget.getZ() + 0.5, 1.1);
            }
        }

        private boolean isNearWater(int radius) {
            BlockPos origin = goose.blockPosition();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (goose.level().getFluidState(origin.offset(dx, 0, dz)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private BlockPos findWater(int radius) {
            BlockPos origin = goose.blockPosition();
            BlockPos closest = null;
            double closestDistSq = Double.MAX_VALUE;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dy = -6; dy <= 6; dy++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (goose.level().getFluidState(candidate).is(FluidTags.WATER)
                                && goose.level().getBlockState(candidate.above()).isAir()
                                && AnchoredPets.isAllowedTarget(goose, candidate)) {
                            double distSq = origin.distSqr(candidate);
                            if (distSq < closestDistSq) {
                                closestDistSq = distSq;
                                closest = candidate;
                            }
                        }
                    }
                }
            }
            return closest;
        }
    }

    /**
     * Rare group relocation: picks a distant body of water and sends this goose
     * and nearby geese towards it, each navigating independently. Wild geese only: a tamed goose
     * never starts a migration and is never swept along by a wild flock.
     */
    private static class GooseFlockMigrationGoal extends Goal {
        private static final int TRIGGER_CHANCE_DENOMINATOR = 6000; // 1-in-N per canUse check
        private static final int MIN_MIGRATION_DISTANCE = 40;
        private static final int MAX_SEARCH_DISTANCE = 120;
        private static final double FLOCK_RADIUS = 16.0;
        private static final int MIGRATION_DURATION_TICKS = 400;

        private final GooseEntity goose;
        private BlockPos migrationTarget;
        private int durationTimer;

        GooseFlockMigrationGoal(GooseEntity goose) {
            this.goose = goose;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (goose.isTame()) return false;
            if (goose.getRandom().nextInt(TRIGGER_CHANCE_DENOMINATOR) != 0) return false;

            migrationTarget = findDistantWater();
            return migrationTarget != null;
        }

        @Override
        public boolean canContinueToUse() {
            return migrationTarget != null && durationTimer < MIGRATION_DURATION_TICKS
                    && goose.distanceToSqr(
                    migrationTarget.getX() + 0.5, migrationTarget.getY(), migrationTarget.getZ() + 0.5) > 4.0;
        }

        @Override
        public void start() {
            durationTimer = 0;
            navigateTo(goose);

            List<GooseEntity> nearbyFlock = goose.level().getEntitiesOfClass(
                    GooseEntity.class, goose.getBoundingBox().inflate(FLOCK_RADIUS),
                    other -> other != goose && !other.isTame());
            for (GooseEntity flockMate : nearbyFlock) {
                navigateTo(flockMate);
            }
        }

        @Override
        public void tick() {
            durationTimer++;
            if (goose.getNavigation().isDone() && migrationTarget != null) {
                navigateTo(goose);
            }
        }

        @Override
        public void stop() {
            migrationTarget = null;
        }

        private void navigateTo(GooseEntity target) {
            if (migrationTarget != null) {
                target.getNavigation().moveTo(
                        migrationTarget.getX() + 0.5, migrationTarget.getY() + 1.0, migrationTarget.getZ() + 0.5, 1.1);
            }
        }

        private BlockPos findDistantWater() {
            BlockPos origin = goose.blockPosition();
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = goose.getRandom().nextDouble() * Math.PI * 2;
                int distance = MIN_MIGRATION_DISTANCE
                        + goose.getRandom().nextInt(MAX_SEARCH_DISTANCE - MIN_MIGRATION_DISTANCE);
                int targetX = origin.getX() + (int) (Math.cos(angle) * distance);
                int targetZ = origin.getZ() + (int) (Math.sin(angle) * distance);

                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        int checkX = targetX + dx;
                        int checkZ = targetZ + dz;
                        int surfaceY = goose.level().getHeight(
                                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                checkX, checkZ) - 1;
                        BlockPos candidate = new BlockPos(checkX, surfaceY, checkZ);
                        if (goose.level().getFluidState(candidate).is(FluidTags.WATER)) {
                            return candidate;
                        }
                    }
                }
            }
            return null;
        }
    }

    private static class GooseWaterRestGoal extends Goal {
        private static final int SEARCH_RADIUS = 8;
        private static final int REST_COOLDOWN_TICKS = 200;

        private final GooseEntity goose;
        private BlockPos restTarget;
        private int cooldown;

        GooseWaterRestGoal(GooseEntity goose) {
            this.goose = goose;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            if (this.goose.getRandom().nextInt(100) != 0) return false;
            if (!AnchoredPets.mayWander(this.goose)) return false;

            this.restTarget = findNearbyWaterSurface();
            return this.restTarget != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.restTarget != null
                    && AnchoredPets.mayWander(this.goose)
                    && this.goose.distanceToSqr(
                    this.restTarget.getX() + 0.5, this.restTarget.getY(), this.restTarget.getZ() + 0.5) > 1.0;
        }

        @Override
        public void start() {
            if (this.restTarget != null) {
                this.goose.getNavigation().moveTo(
                        this.restTarget.getX() + 0.5, this.restTarget.getY() + 1.0, this.restTarget.getZ() + 0.5, 1.0);
            }
        }

        @Override
        public void stop() {
            this.cooldown = REST_COOLDOWN_TICKS;
            this.restTarget = null;
        }

        private BlockPos findNearbyWaterSurface() {
            BlockPos origin = this.goose.blockPosition();
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (this.goose.level().getFluidState(candidate).is(FluidTags.WATER)
                                && this.goose.level().getBlockState(candidate.above()).isAir()
                                && AnchoredPets.isAllowedTarget(this.goose, candidate)) {
                            return candidate;
                        }
                    }
                }
            }
            return null;
        }
    }

    public static boolean checkGooseSpawnRules(net.minecraft.world.entity.EntityType<GooseEntity> type,
                                               net.minecraft.world.level.LevelAccessor level,
                                               net.minecraft.world.entity.EntitySpawnReason reason,
                                               BlockPos pos,
                                               net.minecraft.util.RandomSource random) {
        BlockState below = level.getBlockState(pos.below());
        boolean onWater = level.getFluidState(pos.below()).is(FluidTags.WATER);
        boolean onGrass = below.is(Blocks.GRASS_BLOCK);

        if (!onWater && !onGrass) {
            return false;
        }

        // Geese are the only animal rivers list, so every river animal spawn would be a goose:
        // let only a third through, leaving swamps and marshes as the place to find them.
        if (level.getBiome(pos).is(net.minecraft.world.level.biome.Biomes.RIVER)) {
            return random.nextInt(3) == 0;
        }

        return true;
    }
}
