package com.terrainlab.item;

import com.terrainlab.entity.OversizedMapFrameEntity;
import com.terrainlab.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class OversizedMapFrameItem extends ItemFrameItem {

    private final int size;

    public OversizedMapFrameItem(int size, Item.Properties properties) {
        super(entityTypeForSize(size), properties);

        if (size != 2 && size != 3) {
            throw new IllegalArgumentException(
                    "Oversized map frame size must be 2 or 3"
            );
        }

        this.size = size;
    }

    private static EntityType<? extends ItemFrame> entityTypeForSize(int size) {
        return switch (size) {
            case 2 -> ModEntities.LARGE_MAP_FRAME.get();
            case 3 -> ModEntities.EXTRA_LARGE_MAP_FRAME.get();
            default -> throw new IllegalArgumentException(
                    "Unsupported oversized map frame size: " + size
            );
        };
    }

    public int getSize() {
        return size;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos wallAnchor = context.getClickedPos();
        Direction facing = context.getClickedFace();

        // Item frames must face horizontally away from a wall.
        if (!facing.getAxis().isHorizontal()) {
            return InteractionResult.FAIL;
        }

        // The clicked wall block is the anchor:
        // 2x2 = bottom-left; 3x3 = centre.
        BlockPos entityPos = wallAnchor.relative(facing);

        OversizedMapFrameEntity frame = new OversizedMapFrameEntity(
                entityTypeForSize(size),
                level,
                entityPos,
                facing
        );

        // Checks the complete footprint, wall support and vanilla coexistence.
        if (!frame.survives()) {
            return InteractionResult.FAIL;
        }

        // Client-side placement prediction. The server creates the entity.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!level.addFreshEntity(frame)) {
            return InteractionResult.FAIL;
        }

        if (context.getPlayer() == null
                || !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        return InteractionResult.SUCCESS;
    }
}