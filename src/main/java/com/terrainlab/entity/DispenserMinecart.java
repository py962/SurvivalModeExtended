package com.terrainlab.entity;

import com.terrainlab.mixin.DispenserBlockInvoker;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Minecart with Dispenser: on a powered activator rail it dispenses one item exactly like a
 * dispenser block at the cart's block position facing its firing direction (same behaviour
 * registry, sounds and smoke).
 */
public class DispenserMinecart extends AbstractDispensingMinecart {

    public DispenserMinecart(EntityType<? extends DispenserMinecart> type, Level level) {
        super(type, level);
    }

    @Override
    protected Block displayBlock() {
        return Blocks.DISPENSER;
    }

    @Override
    protected void fire(ServerLevel level, BlockPos pos, Direction direction) {
        BlockState state = Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, direction);
        int slot = this.getRandomSlot(level.getRandom());
        if (slot < 0) {
            level.levelEvent(1001, pos, 0);
            level.gameEvent(GameEvent.BLOCK_ACTIVATE, pos, GameEvent.Context.of(this, state));
            return;
        }
        BlockSource source = new BlockSource(level, pos, state, new CartDispenserBlockEntity(level, pos, direction, this));
        ItemStack stack = this.getItem(slot);
        DispenseItemBehavior behavior = ((DispenserBlockInvoker) Blocks.DISPENSER).terrainlab$getDispenseMethod(level, stack);
        if (behavior != DispenseItemBehavior.NOOP) {
            this.setItem(slot, behavior.dispense(source, stack));
        }
    }

    @Override
    protected Item getDropItem() {
        return ModItems.DISPENSER_MINECART.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.DISPENSER_MINECART.get());
    }
}
