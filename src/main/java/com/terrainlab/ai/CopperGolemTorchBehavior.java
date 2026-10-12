package com.terrainlab.ai;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Copper golem brain behavior: fetches torches from nearby copper chests and places them
 * on dark floor spots (below mob-spawn light level), then returns to the chest.
 */
public class CopperGolemTorchBehavior extends Behavior<CopperGolem> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/CopperGolemTorch]";

    private static final int SEARCH_RADIUS_H = 32;
    private static final int SEARCH_RADIUS_V = 8;
    private static final int DARK_SEARCH_RADIUS = 24;
    private static final int DARK_THRESHOLD = 10; // matches vanilla mob-spawn darkness
    private static final float SPEED_MODIFIER = 1.0F;

    private enum Stage { SEEK_CHEST, SEEK_DARK_SPOT, RETURN_TO_CHEST }

    private Stage stage = Stage.SEEK_CHEST;
    private BlockPos targetChestPos;
    private BlockPos targetDarkPos;

    private static final int MAX_DURATION_TICKS = 1200;    // hard cap on one run
    private static final int RETRY_COOLDOWN_TICKS = 100;   // pause between runs; the block scans are expensive

    private boolean finished;
    private long nextAttemptTime;

    // Game time at start(); used only for trace context.
    private long startGameTime = -1;

    public CopperGolemTorchBehavior() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), 60, MAX_DURATION_TICKS);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, CopperGolem golem) {
        if (level.getGameTime() < this.nextAttemptTime) {
            return false;
        }
        if (golem.isLeashed() || golem.isPanicking()) {
            // LOGGER.trace("{} checkExtraStartConditions: false (leashed={}, panicking={})",
                    // LOG_PREFIX, golem.isLeashed(), golem.isPanicking());
            return false;
        }

        ItemStack held = golem.getMainHandItem();
        if (held.is(Items.TORCH)) {
            // LOGGER.trace("{} checkExtraStartConditions: true (already holding torch, resuming)", LOG_PREFIX);
            return true;
        }
        if (!held.isEmpty()) {
            // LOGGER.trace("{} checkExtraStartConditions: false (hand holds non-torch item: {})",
                    // LOG_PREFIX, held.getItem());
            return false;
        }
        BlockPos found = findChestWithTorch(level, golem);
        boolean result = found != null;
        if (!result) {
            this.nextAttemptTime = level.getGameTime() + RETRY_COOLDOWN_TICKS;
        }
        // LOGGER.trace("{} checkExtraStartConditions: {} (chestWithTorch={})", LOG_PREFIX, result, found);
        return result;
    }

    @Override
    protected void start(ServerLevel level, CopperGolem golem, long gameTime) {
        this.startGameTime = gameTime;
        this.finished = false;
        ItemStack held = golem.getMainHandItem();
        if (held.is(Items.TORCH)) {
            this.stage = Stage.SEEK_DARK_SPOT;
        } else {
            this.targetChestPos = findChestWithTorch(level, golem);
            this.stage = Stage.SEEK_CHEST;
        }
        // LOGGER.trace("{} start() at gameTime={}: stage={}, targetChestPos={}",
                // LOG_PREFIX, gameTime, this.stage, this.targetChestPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, CopperGolem golem, long gameTime) {
        boolean result = !this.finished && !golem.isLeashed() && !golem.isPanicking();
        if (!result) {
            // LOGGER.trace("{} canStillUse: false at gameTime={} (elapsed={} ticks, leashed={}, panicking={})",
                    // LOG_PREFIX, gameTime, gameTime - this.startGameTime, golem.isLeashed(), golem.isPanicking());
        }
        return result;
    }

    @Override
    protected void tick(ServerLevel level, CopperGolem golem, long gameTime) {
        long elapsed = this.startGameTime >= 0 ? gameTime - this.startGameTime : -1;
        // LOGGER.trace("{} tick() gameTime={} elapsed={} stage={} pos={}",
                // LOG_PREFIX, gameTime, elapsed, this.stage, golem.blockPosition());

        switch (this.stage) {
            case SEEK_CHEST -> tickSeekChest(level, golem);
            case SEEK_DARK_SPOT -> tickSeekDarkSpot(level, golem);
            case RETURN_TO_CHEST -> tickReturnToChest(level, golem);
        }
    }

    @Override
    protected void stop(ServerLevel level, CopperGolem golem, long gameTime) {
        this.nextAttemptTime = gameTime + RETRY_COOLDOWN_TICKS;
        long elapsed = this.startGameTime >= 0 ? gameTime - this.startGameTime : -1;
        // LOGGER.trace("{} stop() at gameTime={} (elapsed={} ticks since start) - final stage was {}, targetChestPos={}, targetDarkPos={}",
                // LOG_PREFIX, gameTime, elapsed, this.stage, this.targetChestPos, this.targetDarkPos);
    }

    private void tickSeekChest(ServerLevel level, CopperGolem golem) {
        if (this.targetChestPos == null) {
            this.targetChestPos = findChestWithTorch(level, golem);
            if (this.targetChestPos == null) {
                this.finished = true;
                return;
            }
            // LOGGER.trace("{} tickSeekChest: new targetChestPos={}", LOG_PREFIX, this.targetChestPos);
        }

        double dist = golem.blockPosition().distSqr(this.targetChestPos);
        if (golem.blockPosition().closerThan(this.targetChestPos, 2.0)) {
            // LOGGER.trace("{} tickSeekChest: arrived at chest {} (distSqr={})", LOG_PREFIX, this.targetChestPos, dist);
            BlockEntity be = level.getBlockEntity(this.targetChestPos);
            if (be instanceof ChestBlockEntity chest) {
                ItemStack taken = takeOneTorch(chest);
                if (!taken.isEmpty()) {
                    golem.setItemSlot(EquipmentSlot.MAINHAND, taken);
                    golem.setGuaranteedDrop(EquipmentSlot.MAINHAND);
                    this.stage = Stage.SEEK_DARK_SPOT;
                    this.targetDarkPos = null;
                    // LOGGER.trace("{} tickSeekChest: took torch, stage -> SEEK_DARK_SPOT", LOG_PREFIX);
                } else {
                    // LOGGER.trace("{} tickSeekChest: chest at target had no torch (already emptied?)", LOG_PREFIX);
                }
            } else {
                // LOGGER.trace("{} tickSeekChest: block entity at target is not a ChestBlockEntity: {}",
                        // LOG_PREFIX, be);
            }
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(golem, this.targetChestPos, SPEED_MODIFIER, 1);
            // LOGGER.trace("{} tickSeekChest: walking toward {} (distSqr={})", LOG_PREFIX, this.targetChestPos, dist);
        }
    }

    private void tickSeekDarkSpot(ServerLevel level, CopperGolem golem) {
        if (this.targetDarkPos == null) {
            this.targetDarkPos = findDarkSpot(level, golem);
            if (this.targetDarkPos == null) {
                this.finished = true;
                return;
            }
            // LOGGER.trace("{} tickSeekDarkSpot: new targetDarkPos={}", LOG_PREFIX, this.targetDarkPos);
        }

        double dist = golem.blockPosition().distSqr(this.targetDarkPos);
        if (golem.blockPosition().closerThan(this.targetDarkPos, 2.0)) {
            BlockState below = level.getBlockState(this.targetDarkPos.below());
            BlockState at = level.getBlockState(this.targetDarkPos);
            // LOGGER.trace("{} tickSeekDarkSpot: arrived at {} (isAir={}, belowSolid={})",
                    // LOG_PREFIX, this.targetDarkPos, at.isAir(), below.isSolid());

            if (at.isAir() && below.isSolid()) {
                level.setBlockAndUpdate(this.targetDarkPos, Blocks.TORCH.defaultBlockState());
                golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                // LOGGER.trace("{} tickSeekDarkSpot: placed torch at {}", LOG_PREFIX, this.targetDarkPos);

                BlockPos nextChest = findChestWithTorch(level, golem);
                if (nextChest != null) {
                    this.targetChestPos = nextChest;
                    this.stage = Stage.SEEK_CHEST;
                    // LOGGER.trace("{} tickSeekDarkSpot: more torches available, stage -> SEEK_CHEST (target={})",
                            // LOG_PREFIX, nextChest);
                } else {
                    this.stage = Stage.RETURN_TO_CHEST;
                    // LOGGER.trace("{} tickSeekDarkSpot: no more torches, stage -> RETURN_TO_CHEST", LOG_PREFIX);
                }
            } else {
                // LOGGER.trace("{} tickSeekDarkSpot: spot invalidated (no longer air/solid-below), clearing targetDarkPos", LOG_PREFIX);
                this.targetDarkPos = null;
            }
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(golem, this.targetDarkPos, SPEED_MODIFIER, 1);
            // LOGGER.trace("{} tickSeekDarkSpot: walking toward {} (distSqr={})", LOG_PREFIX, this.targetDarkPos, dist);
        }
    }

    private void tickReturnToChest(ServerLevel level, CopperGolem golem) {
        if (this.targetChestPos == null) {
            this.finished = true;
            return;
        }
        if (!golem.blockPosition().closerThan(this.targetChestPos, 2.0)) {
            BehaviorUtils.setWalkAndLookTargetMemories(golem, this.targetChestPos, SPEED_MODIFIER, 1);
            // LOGGER.trace("{} tickReturnToChest: walking back toward {}", LOG_PREFIX, this.targetChestPos);
        } else {
            this.finished = true;
        }
    }

    private BlockPos findChestWithTorch(ServerLevel level, CopperGolem golem) {
        BlockPos origin = golem.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SEARCH_RADIUS_H; dx <= SEARCH_RADIUS_H; dx++) {
            for (int dz = -SEARCH_RADIUS_H; dz <= SEARCH_RADIUS_H; dz++) {
                for (int dy = -SEARCH_RADIUS_V; dy <= SEARCH_RADIUS_V; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(BlockTags.COPPER_CHESTS)) continue;
                    if (level.getBlockEntity(cursor) instanceof ChestBlockEntity chest && containsTorch(chest)) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private boolean containsTorch(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(Items.TORCH)) return true;
        }
        return false;
    }

    private ItemStack takeOneTorch(Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(Items.TORCH)) {
                return container.removeItem(i, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    private BlockPos findDarkSpot(ServerLevel level, CopperGolem golem) {
        BlockPos origin = golem.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -DARK_SEARCH_RADIUS; dx <= DARK_SEARCH_RADIUS; dx++) {
            for (int dz = -DARK_SEARCH_RADIUS; dz <= DARK_SEARCH_RADIUS; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (!level.getBlockState(cursor).isAir()) continue;
                    if (!level.getBlockState(cursor.below()).isSolid()) continue;
                    if (level.getMaxLocalRawBrightness(cursor) < DARK_THRESHOLD) {
                        // LOGGER.trace("{} findDarkSpot: found {} (brightness < {})",
                                // LOG_PREFIX, cursor, DARK_THRESHOLD);
                        return cursor.immutable();
                    }
                }
            }
        }
        // LOGGER.trace("{} findDarkSpot: no dark spot found within radius {}", LOG_PREFIX, DARK_SEARCH_RADIUS);
        return null;
    }
}
