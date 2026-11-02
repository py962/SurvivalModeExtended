package com.terrainlab.cushion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Places a cushion of this item's colour on top of the clicked block (backport of 26.3's CushionItem). */
public class CushionItem extends Item {
    private static final double COLLISION_SHAPE_RAYCAST_EPSILON = 0.001;

    private final DyeColor color;

    public CushionItem(DyeColor color, Item.Properties properties) {
        super(properties);
        this.color = color;
    }

    public DyeColor getColor() {
        return color;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        UseOnContext recalculated = recalculateContextForSpecialCollisionShapes(context);
        Level level = context.getLevel();
        if (recalculated.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }

        BlockPlaceContext placeContext = new BlockPlaceContext(recalculated);
        BlockPos blockPos = placeContext.getClickedPos();
        Vec3 entityPos = new Vec3(blockPos.getX() + 0.5, recalculated.getClickLocation().y, blockPos.getZ() + 0.5);
        EntityType<CushionEntity> type = Cushions.CUSHION.get();
        AABB spawnAABB = type.getSpawnAABB(entityPos.x, entityPos.y, entityPos.z);
        if (!CushionEntity.canBePlacedAt(level, spawnAABB)) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel serverLevel) {
            if (!serverLevel.getEntitiesOfClass(CushionEntity.class, spawnAABB).isEmpty()) {
                return InteractionResult.FAIL;
            }
            CushionEntity cushion = type.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
            if (cushion == null) {
                return InteractionResult.FAIL;
            }
            cushion.setAnchor(blockPos);
            cushion.setColor(this.color);
            cushion.snapTo(entityPos, Direction.fromYRot(placeContext.getRotation()).toYRot(), 0.0F);
            EntityType.<CushionEntity>createDefaultStackConfig(serverLevel, stack, context.getPlayer()).accept(cushion);
            serverLevel.addFreshEntity(cushion);
            cushion.destroyIfInFire(serverLevel);
            level.playSound(null, cushion.getX(), cushion.getY(), cushion.getZ(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
            cushion.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
            stack.consume(1, placeContext.getPlayer());
        }
        return InteractionResult.SUCCESS;
    }

    /** Cauldrons, hoppers and composters: aim at their collision shape so the cushion sits inside the rim. */
    private static UseOnContext recalculateContextForSpecialCollisionShapes(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return context;
        }
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = level.getBlockState(clickedPos);
        if (!clickedState.is(Cushions.USES_COLLISION_SHAPE)) {
            return context;
        }
        Vec3 rayFrom = player.getEyePosition();
        Vec3 ray = context.getClickLocation().subtract(rayFrom);
        Vec3 rayTo = context.getClickLocation().add(ray.normalize().scale(COLLISION_SHAPE_RAYCAST_EPSILON));
        BlockHitResult hit = clickedState.getCollisionShape(level, clickedPos).clip(rayFrom, rayTo, clickedPos);
        return hit == null ? context : new UseOnContext(player, context.getHand(), hit);
    }
}
