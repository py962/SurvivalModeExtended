package com.terrainlab.blockentity;

import com.terrainlab.menu.AncientCodexMenu;
import com.terrainlab.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Ancient Codex storage (book, lapis, output slots) plus the server-side glyph selection,
 * capped at three glyphs out of eight.
 */
public class AncientCodexBlockEntity extends BlockEntity implements Container, MenuProvider {

    private static final int SLOT_BOOK = 0;
    private static final int SLOT_LAPIS = 1;
    private static final int SLOT_OUTPUT = 2;
    private static final int CONTAINER_SIZE = 3;

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    private final boolean[] selectedGlyphs = new boolean[8];
    private int selectedCount = 0;

    public AncientCodexBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANCIENT_CODEX_BE.get(), pos, state);
    }

    public boolean toggleGlyph(int index) {
        if (index < 0 || index >= selectedGlyphs.length) return false; // index comes from the client
        if (selectedGlyphs[index]) {
            selectedGlyphs[index] = false;
            selectedCount--;
            return true;
        }
        if (selectedCount >= 3) return false;
        selectedGlyphs[index] = true;
        selectedCount++;
        return true;
    }

    public boolean[] getSelectedGlyphs() { return selectedGlyphs; }
    public int getSelectedCount() { return selectedCount; }

    public void resetGlyphs() {
        for (int i = 0; i < 8; i++) selectedGlyphs[i] = false;
        selectedCount = 0;
    }

    public int[] getSelectedIndices() {
        int[] indices = new int[3];
        int j = 0;
        for (int i = 0; i < 8; i++) {
            if (selectedGlyphs[i]) indices[j++] = i;
        }
        return indices;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.terrainlab.ancient_codex");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AncientCodexMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() { return CONTAINER_SIZE; }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = items.get(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (stack.getCount() <= amount) {
            items.set(slot, ItemStack.EMPTY);
            setChanged();
            return stack;
        }
        ItemStack split = stack.split(amount);
        setChanged();
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.replaceAll(s -> ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            items.set(i, input.read("Item" + i, ItemStack.OPTIONAL_CODEC)
                    .orElse(ItemStack.EMPTY));
        }
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            if (!items.get(i).isEmpty()) {
                output.store("Item" + i, ItemStack.OPTIONAL_CODEC, items.get(i));
            }
        }
    }
}
