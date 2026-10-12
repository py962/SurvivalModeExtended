package com.terrainlab.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Idle goal that occasionally sends an adult piglin walking into a nearby Nether portal. Skipped while
 * the brain holds an attack or avoid target.
 * <p>
 * Implemented as a goal because PiglinAi's activities have no injection seam; goal and brain both drive
 * the same navigation, so brief path thrash during idle wandering is expected. The portal search is a
 * cubic scan, gated by cooldown and a random roll.
 */
public class PiglinSeekPortalGoal extends Goal {

    private static final int SEARCH_RADIUS = 16; // blocks; vertical extent is half this
    private static final int COOLDOWN_TICKS = 200;   // canUse() calls between rolls
    private static final float CHANCE_TO_ENTER = 0.15F; // per roll, chance to scan for a portal
    private static final double SPEED_MODIFIER = 0.6D;
    private static final int GIVE_UP_TICKS = 400;    // goal tick() calls before abandoning pursuit

    private final Piglin piglin;
    private BlockPos targetPortalPos;
    private int cooldown;
    private int pursuitTicks;

    public PiglinSeekPortalGoal(Piglin piglin) {
        this.piglin = piglin;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (piglin.isBaby()) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        cooldown = COOLDOWN_TICKS;

        if (piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || piglin.getBrain().hasMemoryValue(MemoryModuleType.AVOID_TARGET)) {
            return false;
        }

        RandomSource random = piglin.getRandom();
        if (random.nextFloat() >= CHANCE_TO_ENTER) {
            return false;
        }

        Optional<BlockPos> found = findNearbyPortal();
        if (found.isEmpty()) {
            return false;
        }

        targetPortalPos = found.get();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (targetPortalPos == null || pursuitTicks > GIVE_UP_TICKS) {
            return false;
        }
        if (piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || piglin.getBrain().hasMemoryValue(MemoryModuleType.AVOID_TARGET)) {
            return false;
        }
        return piglin.level().getBlockState(targetPortalPos).is(Blocks.NETHER_PORTAL);
    }

    @Override
    public void start() {
        pursuitTicks = 0;
        moveTowardTarget();
    }

    @Override
    public void tick() {
        pursuitTicks++;
        if (piglin.getNavigation().isDone()) {
            moveTowardTarget();
        }
    }

    @Override
    public void stop() {
        targetPortalPos = null;
        pursuitTicks = 0;
    }

    private void moveTowardTarget() {
        if (targetPortalPos != null) {
            piglin.getNavigation().moveTo(
                    targetPortalPos.getX() + 0.5,
                    targetPortalPos.getY(),
                    targetPortalPos.getZ() + 0.5,
                    SPEED_MODIFIER);
        }
    }

    private Optional<BlockPos> findNearbyPortal() {
        BlockPos origin = piglin.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS / 2; dy <= SEARCH_RADIUS / 2; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (piglin.level().getBlockState(mutable).is(Blocks.NETHER_PORTAL)) {
                        return Optional.of(mutable.immutable());
                    }
                }
            }
        }
        return Optional.empty();
    }
}
