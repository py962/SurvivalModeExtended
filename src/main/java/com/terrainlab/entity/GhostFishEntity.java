package com.terrainlab.entity;

import com.terrainlab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ghost Fish: a fish that swims through solid blocks like a Vex, using
 * noPhysics, FlyingMoveControl and a straight-line wander goal instead of
 * pathfinding. It breathes inside water and solid terrain and only dries out in open air.
 * Rendering lives in GhostFishRenderer.
 */
public class GhostFishEntity extends AbstractFish {

    public GhostFishEntity(EntityType<? extends AbstractFish> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void registerGoals() {
        // No super.registerGoals(): fish goals assume solid blocks and fight noPhysics.
        this.goalSelector.addGoal(0, new GhostFishWanderGoal(this));
    }

    // Fish travel() falls back to gravity whenever the fish isn't detected as in
    // water, which is common while noclipping; use flying-mob momentum movement instead.
    @Override
    public void travel(Vec3 travelVector) {
        if (this.isAlive()) {
            float speed = (float) this.getAttributeValue(Attributes.FLYING_SPEED);
            this.moveRelative(speed, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.91F));
        }
        this.calculateEntityAnimation(false);
    }

    @Override
    protected void handleAirSupply(ServerLevel level, int airSupply) {
        if (!level.getBlockState(this.blockPosition()).isAir() || this.isInWater()) {
            this.setAirSupply(this.getMaxAirSupply());
            return;
        }
        super.handleAirSupply(level, airSupply);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractFish.createAttributes()
                .add(Attributes.MAX_HEALTH, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.16)
                .add(Attributes.FLYING_SPEED, 0.16);
    }

    /** Depth-only check (below Y 40, in water); biomes are gated by the spawn data. */
    public static boolean checkGhostFishSpawnRules(EntityType<GhostFishEntity> type, ServerLevelAccessor level,
                                                   EntitySpawnReason spawnType, BlockPos pos, RandomSource random) {
        return pos.getY() < 40 && level.getFluidState(pos).is(FluidTags.WATER);
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(ModItems.GHOST_FISH_BUCKET.get());
    }

    @Override
    protected SoundEvent getFlopSound() {
        // TODO: placeholder sound.
        return SoundEvents.COD_FLOP;
    }

    /** Vex-style wander: flies straight at a random nearby point, which may be inside a block. */
    private static class GhostFishWanderGoal extends Goal {
        private final GhostFishEntity fish;

        GhostFishWanderGoal(GhostFishEntity fish) {
            this.fish = fish;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return this.fish.getMoveControl() instanceof FlyingMoveControl
                    && (!this.fish.getMoveControl().hasWanted() || this.fish.getRandom().nextInt(7) == 0);
        }

        @Override
        public boolean canContinueToUse() {
            return this.fish.getMoveControl().hasWanted();
        }

        @Override
        public void start() {
            var random = this.fish.getRandom();
            double x = this.fish.getX() + (random.nextDouble() * 2.0 - 1.0) * 8.0;
            double y = this.fish.getY() + (random.nextDouble() * 2.0 - 1.0) * 4.0;
            double z = this.fish.getZ() + (random.nextDouble() * 2.0 - 1.0) * 8.0;
            this.fish.getMoveControl().setWantedPosition(x, y, z, 0.5);
        }
    }
}
