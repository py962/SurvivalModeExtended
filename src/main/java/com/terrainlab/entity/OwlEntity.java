
package com.terrainlab.entity;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LandOnOwnersShoulderGoal;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.tags.BlockTags;

import java.util.EnumSet;

public class OwlEntity extends Parrot {

    private static final boolean DEBUG_LOGGING = false;

    private static final ResourceKey<LootTable> OWL_MORNING_GIFT =
            ResourceKey.create(
                    Registries.LOOT_TABLE,
                    Identifier.fromNamespaceAndPath(
                            TerrainLab.MODID,
                            "gameplay/owl_morning_gift"
                    )
            );

    private static boolean isDayTime(Level level) {
        long dayTime = level.getDayTime() % 24000L;
        return dayTime >= 2000L && dayTime < 10000L;
    }

    public OwlEntity(EntityType<? extends Parrot> entityType, Level level) {
        super(entityType, level);
    }

    private static final float GLIDE_FACTOR = 2.5F;
    private float owlNextFlap = 1.0F;

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.getAvailableGoals()
                .removeIf(wrapped ->
                        wrapped.getGoal() instanceof LandOnOwnersShoulderGoal
                );

        this.goalSelector.addGoal(1, new OwlNocturnalGoal(this));
        this.goalSelector.addGoal(2, new OwlCircleGoal(this));
        this.goalSelector.addGoal(3, new OwlMorningGiftGoal(this));
        this.goalSelector.addGoal(4, new OwlPerchGoal(this));
    }

    @Override
    protected boolean isFlapping() {
        return this.flyDist > this.owlNextFlap;
    }

    @Override
    protected void onFlap() {
        // Silent: skips the parrot flap sound.
        // TODO: add an owl wing-flap sound.
        this.owlNextFlap =
                this.flyDist + this.flapSpeed / 2.0F * GLIDE_FACTOR;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return ModSounds.OWL_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.OWL_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.OWL_DEATH.get();
    }

    /**
     * Daytime resting:
     * finds an air block on top of the canopy (leaves below, open sky above), moves the owl
     * there so it sits on the leaf surface, and holds it until night
     * or until the resting position becomes invalid.
     */
    private static class OwlNocturnalGoal extends Goal {
        private static final int SEARCH_RADIUS = 16;
        private static final int SEARCH_VERTICAL = 8;
        private static final double REST_SPEED = 1.0D;
        private static final double ARRIVAL_DISTANCE = 0.45D;

        private final OwlEntity owl;

        private BlockPos restPos;
        private boolean resting;
        private boolean previousNoGravity;

        OwlNocturnalGoal(OwlEntity owl) {
            this.owl = owl;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!isDayTime(this.owl.level())
                    || this.owl.isOrderedToSit()) {
                return false;
            }

            this.restPos = findRestPosition();
            return this.restPos != null;
        }

        @Override
        public boolean canContinueToUse() {
            return isDayTime(this.owl.level())
                    && !this.owl.isOrderedToSit()
                    && this.restPos != null
                    && isValidRestPosition(this.restPos);
        }

        @Override
        public void start() {
            this.resting = false;
            this.previousNoGravity = this.owl.isNoGravity();
            this.owl.getNavigation().stop();
            moveTowardRestPosition();
        }

        @Override
        public void tick() {
            if (this.restPos == null) {
                return;
            }

            Vec3 target = Vec3.atBottomCenterOf(this.restPos);

            if (this.owl.position().distanceToSqr(target)
                    <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE) {

                if (!this.resting) {
                    this.resting = true;
                    this.owl.getNavigation().stop();
                    this.owl.setNoGravity(true);
                }

                this.owl.setDeltaMovement(Vec3.ZERO);
                this.owl.setPos(target.x, target.y, target.z);
            } else {
                if (this.resting) {
                    this.resting = false;
                    this.owl.setNoGravity(this.previousNoGravity);
                }

                moveTowardRestPosition();
            }
        }

        @Override
        public void stop() {
            this.owl.getNavigation().stop();
            this.owl.setNoGravity(this.previousNoGravity);
            this.owl.setDeltaMovement(Vec3.ZERO);

            this.resting = false;
            this.restPos = null;
        }

        private void moveTowardRestPosition() {
            if (this.restPos == null) {
                return;
            }

            Vec3 target = Vec3.atBottomCenterOf(this.restPos);

            /*
             * Use the movement controller directly. Ground-style navigation
             * may fail to route to an air block above the canopy.
             */
            this.owl.moveControl.setWantedPosition(
                    target.x,
                    target.y,
                    target.z,
                    REST_SPEED
            );
        }

        private boolean isValidRestPosition(BlockPos pos) {
            Level level = this.owl.level();

            if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
                return false;
            }

            BlockState air = level.getBlockState(pos);
            BlockState leaves = level.getBlockState(pos.below());

            // Open sky above keeps roosts on the top of the canopy, never in pockets inside a crown.
            return air.isAir() && leaves.is(BlockTags.LEAVES) && level.canSeeSky(pos);
        }

        private BlockPos findRestPosition() {
            BlockPos origin = this.owl.blockPosition();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;

            for (int dy = -SEARCH_VERTICAL; dy <= SEARCH_VERTICAL; dy++) {
                for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                    for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);

                        if (!isValidRestPosition(candidate)) {
                            continue;
                        }

                        double distance =
                                candidate.distToCenterSqr(this.owl.position());

                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate.immutable();
                        }
                    }
                }
            }

            return best;
        }
    }

    /**
     * Night-time circling for wild owls.
     */
    private static class OwlCircleGoal extends Goal {
        private static final int CIRCLE_ALTITUDE = 24;
        private static final int CIRCLE_RADIUS = 20;
        private static final int WAYPOINT_INTERVAL_TICKS = 60;
        private static final int RECENTER_INTERVAL_TICKS = 1200;

        private final OwlEntity owl;

        private BlockPos center;
        private int waypointTimer;
        private int recenterTimer;

        OwlCircleGoal(OwlEntity owl) {
            this.owl = owl;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return shouldCircle();
        }

        @Override
        public boolean canContinueToUse() {
            return shouldCircle();
        }

        private boolean shouldCircle() {
            return !isDayTime(this.owl.level())
                    && !this.owl.isTame()
                    && !this.owl.isOrderedToSit();
        }

        @Override
        public void start() {
            pickCenter();
            this.waypointTimer = 0;
            this.recenterTimer = 0;
        }

        @Override
        public void tick() {
            this.recenterTimer++;

            if (this.recenterTimer >= RECENTER_INTERVAL_TICKS) {
                pickCenter();
                this.recenterTimer = 0;
            }

            this.waypointTimer++;

            if (this.waypointTimer >= WAYPOINT_INTERVAL_TICKS
                    || this.owl.getNavigation().isDone()) {
                this.waypointTimer = 0;
                moveToNewOrbitPoint();
            }
        }

        private void pickCenter() {
            BlockPos pos = this.owl.blockPosition();

            int groundY = this.owl.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING,
                    pos.getX(),
                    pos.getZ()
            );

            int y = Math.min(
                    groundY + CIRCLE_ALTITUDE,
                    this.owl.level().getMaxY() - 5
            );

            this.center = new BlockPos(pos.getX(), y, pos.getZ());
        }

        private void moveToNewOrbitPoint() {
            double angle = this.owl.getRandom().nextDouble()
                    * Math.PI * 2.0;

            double x = this.center.getX()
                    + Math.cos(angle) * CIRCLE_RADIUS;

            double z = this.center.getZ()
                    + Math.sin(angle) * CIRCLE_RADIUS;

            this.owl.getNavigation().moveTo(
                    x,
                    this.center.getY(),
                    z,
                    1.0
            );
        }
    }

    /**
     * Drops at most one gift per in-game day during dawn,
     * when the tamed owl's owner is nearby.
     */
    private static class OwlMorningGiftGoal extends Goal {
        private static final double GIFT_CHANCE_PER_CHECK = 0.02;
        private static final double OWNER_RANGE = 10.0;
        private static final long DAWN_START = 23000L;

        private final OwlEntity owl;
        private long lastGiftDay = Long.MIN_VALUE;

        OwlMorningGiftGoal(OwlEntity owl) {
            this.owl = owl;
        }

        @Override
        public boolean canUse() {
            if (!this.owl.isTame()) {
                return false;
            }

            long dayTime = this.owl.level().getDayTime();

            if (dayTime % 24000L < DAWN_START) {
                return false;
            }

            if (dayTime / 24000L == this.lastGiftDay) {
                return false;
            }

            LivingEntity ownerEntity = this.owl.getOwner();

            if (!(ownerEntity instanceof Player player)) {
                return false;
            }

            if (this.owl.distanceToSqr(player)
                    > OWNER_RANGE * OWNER_RANGE) {
                return false;
            }

            return this.owl.getRandom().nextDouble()
                    < GIFT_CHANCE_PER_CHECK;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (this.owl.getOwner() instanceof Player player) {
                this.lastGiftDay =
                        this.owl.level().getDayTime() / 24000L;

                giveGift(player);
            }
        }

        private void giveGift(Player owner) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            pos.set(this.owl.blockPosition());

            this.owl.dropFromGiftLootTable(
                    getServerLevel(this.owl),
                    OWL_MORNING_GIFT,
                    (level, stack) -> level.addFreshEntity(
                            new ItemEntity(
                                    level,
                                    pos.getX() - Mth.sin(
                                            this.owl.yBodyRot
                                                    * (float) (Math.PI / 180.0)
                                    ),
                                    pos.getY(),
                                    pos.getZ() + Mth.cos(
                                            this.owl.yBodyRot
                                                    * (float) (Math.PI / 180.0)
                                    ),
                                    stack
                            )
                    )
            );
        }
    }

    /**
     * Occasional perching on fences or leaves.
     */
    private static class OwlPerchGoal extends Goal {
        private static final int SEARCH_RADIUS = 20;
        private static final int PERCH_COOLDOWN_TICKS = 200;

        private final OwlEntity owl;

        private BlockPos perchTarget;
        private int cooldown;

        OwlPerchGoal(OwlEntity owl) {
            this.owl = owl;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }

            if (this.owl.getRandom().nextInt(100) != 0) {
                return false;
            }

            this.perchTarget = findNearbyFenceTop();

            if (this.perchTarget == null) {
                this.perchTarget = findNearbyTreeTop();
            }

            return this.perchTarget != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.perchTarget != null
                    && this.owl.distanceToSqr(
                    this.perchTarget.getX() + 0.5,
                    this.perchTarget.getY(),
                    this.perchTarget.getZ() + 0.5
            ) > 1.0;
        }

        @Override
        public void start() {
            if (this.perchTarget != null) {
                double surfaceY = topOfBlock(
                        this.owl.level(),
                        this.perchTarget
                );

                this.owl.getNavigation().moveTo(
                        this.perchTarget.getX() + 0.5,
                        surfaceY,
                        this.perchTarget.getZ() + 0.5,
                        1.0
                );
            }
        }

        @Override
        public void stop() {
            this.cooldown = PERCH_COOLDOWN_TICKS;
            this.perchTarget = null;
        }

        private static double topOfBlock(Level level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);

            double topOffset = shape.isEmpty()
                    ? 1.0
                    : shape.max(Direction.Axis.Y);

            return pos.getY() + topOffset;
        }

        private BlockPos findNearbyFenceTop() {
            BlockPos origin = this.owl.blockPosition();

            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        BlockState state =
                                this.owl.level().getBlockState(candidate);

                        if (state.is(BlockTags.FENCES)
                                && this.owl.level()
                                .getBlockState(candidate.above()).isAir()) {
                            return candidate;
                        }
                    }
                }
            }

            return null;
        }

        private BlockPos findNearbyTreeTop() {
            BlockPos origin = this.owl.blockPosition();

            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos candidate = origin.offset(dx, dy, dz);
                        BlockState state =
                                this.owl.level().getBlockState(candidate);

                        if (state.is(BlockTags.LEAVES)
                                && this.owl.level()
                                .getBlockState(candidate.above()).isAir()) {
                            return candidate;
                        }
                    }
                }
            }

            return null;
        }
    }

    public static boolean checkOwlSpawnRules(
            EntityType<OwlEntity> type,
            LevelAccessor level,
            net.minecraft.world.entity.EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random
    ) {
        BlockState below = level.getBlockState(pos.below());

        boolean onSnow =
                below.is(Blocks.SNOW)
                        || below.is(Blocks.SNOW_BLOCK);

        boolean onGround =
                below.is(Blocks.GRASS_BLOCK)
                        || below.is(Blocks.DIRT)
                        || below.is(Blocks.PODZOL)
                        || below.is(Blocks.COARSE_DIRT);

        boolean onLeaves = below.is(BlockTags.LEAVES);

        boolean result = onSnow || onGround || onLeaves;

        if (DEBUG_LOGGING) {
            // TerrainLab.LOGGER.info(
            //     "[OWL_SPAWN] check at {}: belowBlock={}, result={}, reason={}",
            //     pos, below.getBlock(), result, reason
            // );
        }

        return result;
    }
}
