package com.terrainlab.entity;

import com.terrainlab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ContainerOrHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;

import java.util.List;

/**
 * Minecart with Dropper: on a powered activator rail it drops one item like a dropper block, or,
 * as DropperBlock#dispenseFrom does, pushes it into a container (block or container entity, never
 * itself) in front of it.
 */
public class DropperMinecart extends AbstractDispensingMinecart {

    private static final DispenseItemBehavior DROP = new DefaultDispenseItemBehavior();

    public DropperMinecart(EntityType<? extends DropperMinecart> type, Level level) {
        super(type, level);
    }

    @Override
    protected Block displayBlock() {
        return Blocks.DROPPER;
    }

    @Override
    protected void fire(ServerLevel level, BlockPos pos, Direction direction) {
        int slot = this.getRandomSlot(level.getRandom());
        if (slot < 0) {
            level.levelEvent(1001, pos, 0);
            return;
        }
        ItemStack stack = this.getItem(slot);
        BlockState state = Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, direction);
        ContainerOrHandler into = this.targetAt(level, pos.relative(direction), direction.getOpposite());
        ItemStack remaining;
        if (into.isEmpty()) {
            BlockSource source = new BlockSource(level, pos, state, new CartDispenserBlockEntity(level, pos, direction, this));
            remaining = DROP.dispense(source, stack);
        } else {
            ItemStack rest = into.container() != null
                    ? HopperBlockEntity.addItem(this, into.container(), stack.copyWithCount(1), direction.getOpposite())
                    : ItemUtil.insertItemReturnRemaining(into.itemHandler(), stack.copyWithCount(1), false, null);
            remaining = stack.copy();
            if (rest.isEmpty()) {
                remaining.shrink(1);
            }
        }
        this.setItem(slot, remaining);
    }

    /** HopperBlockEntity#getContainerOrHandlerAt, but an entity target is never this cart. */
    private ContainerOrHandler targetAt(ServerLevel level, BlockPos target, Direction side) {
        ContainerOrHandler found = HopperBlockEntity.getContainerOrHandlerAt(level, target, side);
        if (found.container() != this) {
            return found;
        }
        List<Entity> entities = level.getEntities(this, new AABB(target), entity ->
                entity.isAlive() && (entity instanceof Container
                        || entity.getCapability(Capabilities.Item.ENTITY_AUTOMATION, side) != null));
        if (entities.isEmpty()) {
            return ContainerOrHandler.EMPTY;
        }
        Entity entity = entities.get(level.getRandom().nextInt(entities.size()));
        if (entity instanceof Container container) {
            return new ContainerOrHandler(container, null);
        }
        ResourceHandler<ItemResource> handler = entity.getCapability(Capabilities.Item.ENTITY_AUTOMATION, side);
        return handler != null ? new ContainerOrHandler(null, handler) : ContainerOrHandler.EMPTY;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.DROPPER_MINECART.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.DROPPER_MINECART.get());
    }
}
