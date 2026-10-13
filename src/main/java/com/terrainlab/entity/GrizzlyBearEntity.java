package com.terrainlab.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.List;

/**
 * Polar-bear-based grizzly. Feeding it a thrown salmon anchors it (persistent, wanders
 * only within the chunk it was fed in), and it one-shots creepers it targets. The anchor is
 * stored as the mob's home area, which vanilla saves and which all wander goals respect.
 */
public class GrizzlyBearEntity extends PolarBear {

    private static final int ANCHOR_RADIUS = 8; // blocks: roughly the fed-in chunk

    public GrizzlyBearEntity(EntityType<? extends PolarBear> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new GrizzlyAnchoredWanderGoal(this));
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0));
        this.goalSelector.addGoal(2, new GrizzlyBearEatSalmonGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Creeper.class, true, null));
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (target instanceof Creeper) {
            target.hurt(this.damageSources().mobAttack(this), 1000.0F);
            return true;
        }
        return super.doHurtTarget(level, target);
    }

    void onFedSalmon() {
        ChunkPos chunk = ChunkPos.containing(this.blockPosition());
        this.setHomeTo(chunk.getMiddleBlockPosition(this.getBlockY()), ANCHOR_RADIUS);
        this.setPersistenceRequired();
    }

    boolean isAnchored() {
        return this.hasHome();
    }

    ChunkPos getAnchorChunk() {
        return this.hasHome() ? ChunkPos.containing(this.getHomePosition()) : null;
    }

    /** Walks to a nearby dropped salmon and eats it, anchoring the bear. */
    private static class GrizzlyBearEatSalmonGoal extends Goal {
        private static final double SEARCH_RADIUS = 8.0;
        private static final double EAT_RADIUS_SQ = 2.5;

        private final GrizzlyBearEntity bear;
        private ItemEntity targetItem;

        GrizzlyBearEatSalmonGoal(GrizzlyBearEntity bear) {
            this.bear = bear;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.bear.isAnchored()) return false; // one-time trigger only
            List<ItemEntity> items = this.bear.level().getEntitiesOfClass(ItemEntity.class,
                    this.bear.getBoundingBox().inflate(SEARCH_RADIUS),
                    item -> item.isAlive() && item.getItem().is(Items.SALMON));
            if (items.isEmpty()) return false;
            this.targetItem = items.get(0);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.bear.isAnchored() && this.targetItem != null && this.targetItem.isAlive();
        }

        @Override
        public void tick() {
            if (this.targetItem == null) return;
            this.bear.getNavigation().moveTo(this.targetItem, 1.0);
            if (this.bear.distanceToSqr(this.targetItem) < EAT_RADIUS_SQ) {
                this.targetItem.discard();
                this.bear.onFedSalmon();
                this.targetItem = null;
            }
        }

        @Override
        public void stop() {
            this.targetItem = null;
        }
    }

    /** Once anchored, picks random wander targets inside the anchor chunk. */
    private static class GrizzlyAnchoredWanderGoal extends Goal {
        private final GrizzlyBearEntity bear;

        GrizzlyAnchoredWanderGoal(GrizzlyBearEntity bear) {
            this.bear = bear;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.bear.isAnchored()
                    && this.bear.getNavigation().isDone()
                    && this.bear.getRandom().nextInt(30) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.bear.getNavigation().isDone();
        }

        @Override
        public void start() {
            ChunkPos chunk = this.bear.getAnchorChunk();
            if (chunk == null) return;
            RandomSource random = this.bear.getRandom();
            int targetX = chunk.getMinBlockX() + random.nextInt(16);
            int targetZ = chunk.getMinBlockZ() + random.nextInt(16);
            this.bear.getNavigation().moveTo(targetX + 0.5, this.bear.getY(), targetZ + 0.5, 1.0);
        }
    }

    // Ground check only; see CaribouEntity#checkCaribouSpawnRules.
    public static boolean checkGrizzlyBearSpawnRules(net.minecraft.world.entity.EntityType<GrizzlyBearEntity> type,
                                                     net.minecraft.world.level.LevelAccessor level,
                                                     net.minecraft.world.entity.EntitySpawnReason reason,
                                                     net.minecraft.core.BlockPos pos,
                                                     net.minecraft.util.RandomSource random) {
        // Grass, plus podzol so they spawn across taiga floors.
        net.minecraft.world.level.block.state.BlockState below = level.getBlockState(pos.below());
        return below.is(net.minecraft.tags.BlockTags.ANIMALS_SPAWNABLE_ON)
                || below.is(net.minecraft.world.level.block.Blocks.PODZOL);
    }
}
