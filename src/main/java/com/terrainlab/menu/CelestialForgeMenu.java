package com.terrainlab.menu;

import com.terrainlab.blockentity.CelestialForgeBlockEntity;
import com.terrainlab.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The Celestial Forge's single gem slot; placing a gem there starts the ritual. */
public class CelestialForgeMenu extends AbstractContainerMenu {

    public static final int GEM_X = 80, GEM_Y = 35;   // must match CelestialForgeScreen
    public static final int INVENTORY_Y = 84;

    private final CelestialForgeBlockEntity blockEntity;

    public CelestialForgeMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, clientBlockEntity(inventory, buf.readBlockPos()));
    }

    private static CelestialForgeBlockEntity clientBlockEntity(Inventory inventory, net.minecraft.core.BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof CelestialForgeBlockEntity forge) {
            return forge;
        }
        throw new IllegalStateException("No Celestial Forge at " + pos);
    }

    public CelestialForgeMenu(int id, Inventory inventory, CelestialForgeBlockEntity blockEntity) {
        super(ModMenuTypes.CELESTIAL_FORGE_MENU.get(), id);
        this.blockEntity = blockEntity;

        addSlot(new Slot(blockEntity, CelestialForgeBlockEntity.SLOT_GEM, GEM_X, GEM_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return blockEntity.canPlaceItem(CelestialForgeBlockEntity.SLOT_GEM, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    public boolean isFor(CelestialForgeBlockEntity forge) {
        return this.blockEntity == forge;
    }

    /** Shift-click moves one gem into the forge, or the forge's gem back to the inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }
}
