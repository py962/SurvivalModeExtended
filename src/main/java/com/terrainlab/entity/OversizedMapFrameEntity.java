package com.terrainlab.entity;

import com.terrainlab.registry.ModEntities;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
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

        // Preserve vanilla item-frame coexistence validation.
        return canCoexist(false);
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
            // Sneaking with an empty hand removes the displayed item normally.
            if (player.isShiftKeyDown() && held.isEmpty()) {
                return super.interact(player, hand, location);
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