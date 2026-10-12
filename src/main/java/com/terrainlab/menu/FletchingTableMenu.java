package com.terrainlab.menu;

import com.terrainlab.blockentity.FletchingTableBlockEntity;
import com.terrainlab.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Fletching table menu: feather, stick, flint and optional potion inputs producing tiered arrows. */
public class FletchingTableMenu extends AbstractContainerMenu {

    private final FletchingTableBlockEntity blockEntity;

    public FletchingTableMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, getBlockEntity(inventory, buf));
    }

    private static FletchingTableBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        return (FletchingTableBlockEntity) be;
    }

    public FletchingTableMenu(int id, Inventory inventory, FletchingTableBlockEntity blockEntity) {
        super(ModMenuTypes.FLETCHING_TABLE_MENU.get(), id);
        this.blockEntity = blockEntity;

        addSlot(new Slot(blockEntity, FletchingTableBlockEntity.SLOT_FEATHER, 31, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FletchingTableBlockEntity.isValidFeather(stack);
            }
        });

        addSlot(new Slot(blockEntity, FletchingTableBlockEntity.SLOT_STICK, 49, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FletchingTableBlockEntity.isValidStick(stack);
            }
        });

        addSlot(new Slot(blockEntity, FletchingTableBlockEntity.SLOT_FLINT, 67, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FletchingTableBlockEntity.isValidFlint(stack);
            }
        });

        // Potion slot may stay empty.
        addSlot(new Slot(blockEntity, FletchingTableBlockEntity.SLOT_POTION, 85, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return FletchingTableBlockEntity.isValidPotion(stack);
            }
        });

        addSlot(new Slot(blockEntity, FletchingTableBlockEntity.SLOT_OUTPUT, 125, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            // Each take consumes a full set of inputs, so the whole output stack is always removed.
            @Override
            public ItemStack remove(int amount) {
                return super.remove(this.getItem().getCount());
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                blockEntity.consumeInputs();
                super.onTake(player, stack);
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "specialist_arrow");
                }
            }
        });

        // Slots sit 1px right/down of vanilla's usual offsets to match this GUI texture's boxes.
        for
 (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9,
                        9 + col * 18, 85 + row * 18));
            }
        }

        // Hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 9 + col * 18, 143));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }
}
