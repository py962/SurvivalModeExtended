package com.terrainlab.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

/**
 * A throwaway, never-placed dispenser block entity standing in for a dispensing minecart in a
 * vanilla {@code BlockSource}: dispense behaviours that put items back (empty buckets, bottles,
 * leftovers) call {@link #insertItem}, and every container access is forwarded to the cart's own
 * inventory, so nothing is ever held here.
 */
final class CartDispenserBlockEntity extends DispenserBlockEntity {

    private final AbstractDispensingMinecart cart;

    CartDispenserBlockEntity(ServerLevel level, BlockPos pos, Direction facing, AbstractDispensingMinecart cart) {
        super(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, facing));
        this.cart = cart;
        this.setLevel(level);
    }

    @Override
    public int getContainerSize() {
        return this.cart.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.cart.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.cart.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return this.cart.removeItem(slot, count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.cart.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack itemStack) {
        this.cart.setItem(slot, itemStack);
    }

    @Override
    public void setItem(int slot, ItemStack itemStack, boolean insideTransaction) {
        this.cart.setItem(slot, itemStack);
    }

    @Override
    public void clearContent() {
        this.cart.clearContent();
    }

    @Override
    public int getRandomSlot(RandomSource random) {
        return this.cart.getRandomSlot(random);
    }

    @Override
    public ItemStack insertItem(ItemStack itemStack) {
        return this.cart.insertItem(itemStack);
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void setChanged() {
        // Not in the world: no chunk to mark dirty and no comparators to update.
    }
}
