
package com.terrainlab.entity;

import com.terrainlab.TerrainLab;
import com.terrainlab.pet.AnchoredPets;
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

/**
 * Owl: nocturnal Parrot-based bird. Tamed owls are bed-anchored pets (see
 * {@link AnchoredPets}): they only roost and perch inside their roaming zone,
 * never circle, and none of these goals move them while they sit or while they
 * are outside the zone.
 */
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

    /**
     * End game-time tick for the daytime circling fallback.
     * The owl retries finding a roost when this time is reached.
     */
    private long daytimeCircleUntil;

    private boolean isDaytimeCircleFallbackActive() {
        return level().getGameTime() < daytimeCircleUntil;
    }

    private static boolean isDayTime(Level level) {
        long dayTime = level.getOverworldClockTime() % 24000L;
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

        // Tamed birds never ride the owner's shoulder.
        this.goalSelector.removeAllGoals(
                goal -> goal instanceof LandOnOwnersShoulderGoal
        );

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
     * Daytime resting: flies to the top of a nearby tree canopy and sits there until dusk.
     * <p>
     * Roosts are the topmost leaves of nearby columns (the heightmap top, so always open sky). Only a roost the
     * owl's flying pathfinding can actually reach is chosen, and the owl flies there along that path rather than
     * in a straight line, so it never pushes into the underside of a canopy or a trunk. On arrival it lands on
     * the leaves with gravity, stops and keeps its head still. If it stops making progress for about 5 seconds
     * it gives up on that roost and circles for a while (see OwlCircleGoal) before searching again.
     */
    private static class OwlNocturnalGoal extends Goal {
        private static final int SEARCH_RADIUS = 16;
        private static final int SEARCH_INTERVAL_TICKS = 40;
        private static final int MAX_PATH_TRIES = 8;
        private static final double REST_SPEED = 1.0D;
        private static final double LAND_DISTANCE = 1.2D;
        private static final int PROGRESS_CHECK_TICKS = 100;
        private static final double MIN_PROGRESS = 1.0D;
        private static final long FALLBACK_CIRCLE_TICKS = 600L;

        private final OwlEntity owl;

        private BlockPos restPos;
        private net.minecraft.world.level.pathfinder.Path path;
        private boolean resting;
        private long nextSearchAt;
        private int progressTimer;
        private double lastDistance;
        private boolean stuck;

        OwlNocturnalGoal(OwlEntity owl) {
            this.owl = owl;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        /**
         * Tick every tick, not every other tick (the goal selector's default). The flying move control runs every
         * tick and drops back to WAIT, with gravity on, whenever no new wanted position arrived that tick, so a
         * goal steering it every other tick makes the owl alternately lift and fall in place and turn back and
         * forth: the hovering, spinning owl that never leaves its block.
         */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!isDayTime(this.owl.level())
                    || this.owl.isOrderedToSit()
                    || AnchoredPets.isTameAndAwayFromHome(this.owl)
                    || this.owl.isDaytimeCircleFallbackActive()) {
                return false;
            }
            long now = this.owl.level().getGameTime();
            if (now < this.nextSearchAt) {
                return false;
            }
            this.nextSearchAt = now + SEARCH_INTERVAL_TICKS;

            if (!findReachableRoost()) {
                this.owl.daytimeCircleUntil = now + FALLBACK_CIRCLE_TICKS;
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return isDayTime(this.owl.level())
                    && !this.owl.isOrderedToSit()
                    && !this.stuck
                    && this.restPos != null
                    && AnchoredPets.isAllowedTarget(this.owl, this.restPos)
                    && isValidRestPosition(this.restPos);
        }

        @Override
        public void start() {
            this.resting = false;
            this.stuck = false;
            this.progressTimer = 0;
            this.lastDistance = distanceToRoost();
            this.owl.getNavigation().moveTo(this.path, REST_SPEED);
        }

        @Override
        public void tick() {
            if (this.restPos == null) {
                return;
            }
            Vec3 target = Vec3.atBottomCenterOf(this.restPos);
            double distance = distanceToRoost();

            if (this.resting) {
                // Sit still on the leaves: no drifting, no head turning.
                this.owl.getNavigation().stop();
                this.owl.setDeltaMovement(0.0D, Math.min(0.0D, this.owl.getDeltaMovement().y), 0.0D);
                if (distance > LAND_DISTANCE * 2.0D) {
                    this.resting = false; // pushed off: fly back
                    repath();
                }
                return;
            }

            if (distance <= LAND_DISTANCE) {
                // Close enough: settle onto the leaf surface under normal gravity.
                this.owl.getNavigation().stop();
                this.owl.getMoveControl().setWantedPosition(target.x, target.y, target.z, 0.5D);
                if (this.owl.onGround()) {
                    this.resting = true;
                }
                return;
            }

            if (this.owl.getNavigation().isDone()) {
                repath();
            }
            this.owl.getLookControl().setLookAt(target.x, target.y, target.z);

            if (++this.progressTimer >= PROGRESS_CHECK_TICKS) {
                this.progressTimer = 0;
                if (this.lastDistance - distance < MIN_PROGRESS) {
                    this.stuck = true;
                    this.owl.daytimeCircleUntil = this.owl.level().getGameTime() + FALLBACK_CIRCLE_TICKS;
                }
                this.lastDistance = distance;
            }
        }

        @Override
        public void stop() {
            this.owl.getNavigation().stop();
            this.resting = false;
            this.restPos = null;
            this.path = null;
        }

        private double distanceToRoost() {
            return this.restPos == null ? 0.0D
                    : Math.sqrt(this.owl.position().distanceToSqr(Vec3.atBottomCenterOf(this.restPos)));
        }

        private void repath() {
            net.minecraft.world.level.pathfinder.Path fresh = this.owl.getNavigation().createPath(this.restPos, 0);
            if (fresh != null && fresh.canReach()) {
                this.path = fresh;
                this.owl.getNavigation().moveTo(fresh, REST_SPEED);
            } else {
                this.stuck = true;
                this.owl.daytimeCircleUntil = this.owl.level().getGameTime() + FALLBACK_CIRCLE_TICKS;
            }
        }

        private boolean isValidRestPosition(BlockPos pos) {
            Level level = this.owl.level();
            if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
                return false;
            }
            // Roost on top of the canopy, not inside the leaves.
            return level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.below()).is(BlockTags.LEAVES)
                    && level.canSeeSky(pos);
        }

        /**
         * Nearest canopy tops first (one candidate per column: the block above its topmost leaves), keeping the
         * first that pathfinding can reach. Cheap: one heightmap read per column, a few path searches at most.
         */
        private boolean findReachableRoost() {
            Level level = this.owl.level();
            BlockPos origin = this.owl.blockPosition();
            java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
                    if (isValidRestPosition(top) && AnchoredPets.isAllowedTarget(this.owl, top)) {
                        candidates.add(top);
                    }
                }
            }
            candidates.sort(java.util.Comparator.comparingDouble(p -> p.distToCenterSqr(this.owl.position())));
            int tries = 0;
            for (BlockPos candidate : candidates) {
                if (tries++ >= MAX_PATH_TRIES) {
                    break;
                }
                net.minecraft.world.level.pathfinder.Path found = this.owl.getNavigation().createPath(candidate, 0);
                if (found != null && found.canReach()) {
                    this.restPos = candidate;
                    this.path = found;
                    return true;
                }
            }
            this.restPos = null;
            this.path = null;
            return false;
        }
    }

    /**
     * Circling for wild owls at night, or temporarily during the day when
     * no suitable roost can be found.
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
            return (!isDayTime(this.owl.level())
                    || this.owl.isDaytimeCircleFallbackActive())
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

            long dayTime = this.owl.level().getOverworldClockTime();

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
                        this.owl.level().getOverworldClockTime() / 24000L;

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

            if (!AnchoredPets.mayWander(this.owl)) {
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
                    && AnchoredPets.mayWander(this.owl)
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
                                .getBlockState(candidate.above()).isAir()
                                && AnchoredPets.isAllowedTarget(
                                this.owl, candidate)) {
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
                                .getBlockState(candidate.above()).isAir()
                                && AnchoredPets.isAllowedTarget(
                                this.owl, candidate)) {
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
        // Owls only spawn on tree tops (the spawn placement uses the MOTION_BLOCKING heightmap, so this is the
        // top of the canopy), so a new owl starts out on a roost: on leaves, or on a snow layer lying on leaves.
        BlockState below = level.getBlockState(pos.below());

        boolean onLeaves = below.is(BlockTags.LEAVES);

        boolean onSnowyLeaves = below.is(Blocks.SNOW)
                && level.getBlockState(pos.below(2)).is(BlockTags.LEAVES);

        boolean result = onLeaves || onSnowyLeaves;

        if (DEBUG_LOGGING) {
            // TerrainLab.LOGGER.info(
            //     "[OWL_SPAWN] check at {}: belowBlock={}, result={}, reason={}",
            //     pos, below.getBlock(), result, reason
            // );
        }

        return result;
    }
}
