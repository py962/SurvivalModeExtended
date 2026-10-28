package com.terrainlab.item;

import com.terrainlab.entity.OversizedMapFrameEntity;
import com.terrainlab.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

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
        BlockPos clickedPos = context.getClickedPos();
        Direction facing = context.getClickedFace();
        Player player = context.getPlayer();
        ItemStack itemInHand = context.getItemInHand();

        // The block in front of the clicked face is the anchor:
        // 3x3 = centre; 2x2 = corner, growing to the placer's right and
        // up (walls) or away from them (floors / ceilings).
        BlockPos entityPos = clickedPos.relative(facing);

        OversizedMapFrameEntity frame = new OversizedMapFrameEntity(
                entityTypeForSize(size),
                level,
                entityPos,
                facing
        );

        // Floor / ceiling frames are oriented by the placer's facing.
        if (facing.getAxis().isVertical()) {
            frame.setFlatFacing(context.getHorizontalDirection());
        }

        // Vanilla ItemFrameItem checks (build height, mayUseItemAt) plus
        // spawn protection / world border, for every footprint block.
        for (BlockPos block : frame.getFootprint()) {
            if (level.isOutsideBuildHeight(block)) {
                return InteractionResult.FAIL;
            }

            if (player != null
                    && (!mayPlace(player, facing, itemInHand, block)
                    || !level.mayInteract(player, block))) {
                return InteractionResult.FAIL;
            }
        }

        // Copy custom name / entity data from the item, as vanilla does.
        EntityType.<OversizedMapFrameEntity>createDefaultStackConfig(
                level,
                itemInHand,
                player
        ).accept(frame);

        // The clicked block must support it; the footprint must be clear.
        if (!frame.canPlace()) {
            return InteractionResult.FAIL;
        }

        // Client-side placement prediction. The server creates the entity.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        frame.playPlacementSound();
        level.gameEvent(player, GameEvent.ENTITY_PLACE, frame.position());

        if (!level.addFreshEntity(frame)) {
            return InteractionResult.FAIL;
        }

        if (player == null || !player.getAbilities().instabuild) {
            itemInHand.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }
}
