package com.terrainlab.item;

import com.terrainlab.entity.AbstractDispensingMinecart;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Item for the dispenser / dropper minecarts: vanilla MinecartItem placement, plus aiming the new
 * cart from the placing player's look (AbstractDispensingMinecart#aimFromPlacement).
 */
public class DispensingMinecartItem extends MinecartItem {

    private final EntityType<? extends AbstractDispensingMinecart> type;

    public DispensingMinecartItem(EntityType<? extends AbstractDispensingMinecart> type, Properties properties) {
        super(type, properties);
        this.type = type;
    }

    // Mirrors MinecartItem#useOn; the cart is aimed before it is added to the level.
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState blockState = level.getBlockState(pos);
        if (!blockState.is(BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        }

        ItemStack itemStack = context.getItemInHand();
        RailShape shape = blockState.getBlock() instanceof BaseRailBlock rail
                ? rail.getRailDirection(blockState, level, pos, null)
                : RailShape.NORTH_SOUTH;
        double offset = shape.isSlope() ? 0.5 : 0.0;
        Vec3 spawnPos = new Vec3(pos.getX() + 0.5, pos.getY() + 0.0625 + offset, pos.getZ() + 0.5);
        Player player = context.getPlayer();
        AbstractDispensingMinecart cart = AbstractMinecart.createMinecart(
                level, spawnPos.x, spawnPos.y, spawnPos.z, this.type, EntitySpawnReason.DISPENSER, itemStack, player);
        if (cart == null) {
            return InteractionResult.FAIL;
        }

        if (AbstractMinecart.useExperimentalMovement(level)) {
            for (Entity entity : level.getEntities(null, cart.getBoundingBox())) {
                if (entity instanceof AbstractMinecart) {
                    return InteractionResult.FAIL;
                }
            }
        }

        if (player != null) {
            cart.aimFromPlacement(player, shape);
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(cart);
            serverLevel.gameEvent(GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(player, serverLevel.getBlockState(pos.below())));
        }

        itemStack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
