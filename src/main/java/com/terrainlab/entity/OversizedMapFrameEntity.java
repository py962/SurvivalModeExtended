package com.terrainlab.entity;

import com.terrainlab.registry.ModEntities;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class OversizedMapFrameEntity extends ItemFrame {

    public OversizedMapFrameEntity(
            EntityType<? extends ItemFrame> type,
            Level level
    ) {
        super(type, level);
    }

    public OversizedMapFrameEntity(
            EntityType<? extends ItemFrame> type,
            Level level,
            BlockPos pos,
            Direction direction
    ) {
        super(type, level, pos, direction);
    }

    public int getFrameSize() {
        return getType() == ModEntities.EXTRA_LARGE_MAP_FRAME.get() ? 3 : 2;
    }

    /**
     * Horizontal axis along which the frame extends.
     */
    public Direction getHorizontalFrameAxis() {
        return getDirection().getCounterClockWise();
    }

    /**
     * Wall block used as the placement anchor.
     */
    public BlockPos getWallAnchor() {
        return getPos().relative(getDirection().getOpposite());
    }

    /**
     * Horizontal footprint offset relative to the anchor.
     */
    public int getFootprintStart() {
        return getFrameSize() == 3 ? -1 : 0;
    }

    /**
     * Bounding box covering the whole 2x2 / 3x3 footprint on the wall,
     * using the same anchor convention as survives() (2x2 = bottom-left,
     * 3x3 = centre). This is used for interaction, hit detection and
     * render culling. The entity position becomes the footprint centre.
     */
    @Override
    protected AABB calculateBoundingBox(BlockPos blockPos, Direction direction) {
        if (!direction.getAxis().isHorizontal()) {
            return super.calculateBoundingBox(blockPos, direction);
        }

        Direction horizontal = direction.getCounterClockWise();
        int start = getFootprintStart();
        int end = start + getFrameSize() - 1;

        Vec3 first = Vec3.atCenterOf(
                blockPos.relative(horizontal, start).above(start)
        ).relative(direction, -0.46875);
        Vec3 last = Vec3.atCenterOf(
                blockPos.relative(horizontal, end).above(end)
        ).relative(direction, -0.46875);

        boolean alongX = direction.getAxis() == Direction.Axis.X;

        return new AABB(first, last).inflate(
                alongX ? 0.03125 : 0.5,
                0.5,
                alongX ? 0.5 : 0.03125
        );
    }

    /**
     * Placement / survival checks use the full footprint, not the
     * vanilla 0.75 block pop box.
     */
    @Override
    protected AABB getPopBox() {
        return calculateBoundingBox(getPos(), getDirection());
    }

    /**
     * The entity centre is not inside its anchor block for 2x2 frames,
     * so sync the anchor block position (as vanilla paintings do).
     */
    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(getPos());
    }

    /**
     * Checks support for every wall block covered by the frame.
     *
     * 2x2: two blocks across and two blocks upward from the bottom-left.
     * 3x3: one block in every direction from the centre.
     */
    @Override
    public boolean survives() {
        Direction facing = getDirection();

        if (!facing.getAxis().isHorizontal()) {
            return false;
        }

        BlockPos anchor = getWallAnchor();
        Direction horizontal = getHorizontalFrameAxis();

        int size = getFrameSize();
        int horizontalStart = getFootprintStart();
        int verticalStart = getFootprintStart();

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                BlockPos support = anchor
                        .relative(horizontal, horizontalStart + x)
                        .above(verticalStart + y);

                if (!level().getBlockState(support)
                        .isFaceSturdy(level(), support, facing)) {
                    return false;
                }
            }
        }

        AABB footprint = getPopBox();

        // The space in front of the wall must be clear, as for vanilla frames.
        if (hasLevelCollision(footprint)) {
            return false;
        }

        // No other hanging entity (item frame, painting, large frame)
        // may overlap any part of the footprint.
        return !level().hasEntities(
                EntityTypeTest.forClass(HangingEntity.class),
                footprint,
                other -> other != this
        );
    }

    /**
     * True for frames that cannot be modified by players (e.g. "Fixed"
     * or invulnerable frames). hurtClient has no side effects.
     */
    private boolean isLocked() {
        return !hurtClient(damageSources().generic());
    }

    private static boolean isSupportedDisplayItem(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof MapItem
                || stack.is(ModItems.ATLAS.get()));
    }

    @Override
    public InteractionResult interact(
            Player player,
            InteractionHand hand,
            Vec3 location
    ) {
        ItemStack held = player.getItemInHand(hand);

        if (!getItem().isEmpty()) {
            // Sneaking with an empty hand removes the displayed item,
            // exactly as punching a vanilla frame does (drops it, or
            // just clears it in creative).
            if (player.isShiftKeyDown() && held.isEmpty()) {
                if (isLocked()) {
                    return InteractionResult.PASS;
                }

                if (level() instanceof ServerLevel serverLevel
                        && serverLevel.mayInteract(player, getPos())) {
                    hurtServer(
                            serverLevel,
                            damageSources().playerAttack(player),
                            0.0F
                    );
                }

                return InteractionResult.SUCCESS;
            }

            // Do not let clicking rotate an already-filled frame.
            return InteractionResult.SUCCESS;
        }

        // Only accept map items and the TerrainLab Atlas.
        if (!isSupportedDisplayItem(held)) {
            return InteractionResult.FAIL;
        }

        // Vanilla handles insertion, map data and item-frame persistence.
        return super.interact(player, hand, location);
    }

    @Override
    public int getAnalogOutput() {
        return 0;
    }

    @Override
    protected ItemStack getFrameItemStack() {
        if (getFrameSize() == 3) {
            return new ItemStack(ModItems.EXTRA_LARGE_ITEM_FRAME.get());
        }

        return new ItemStack(ModItems.LARGE_ITEM_FRAME.get());
    }
}