package com.terrainlab.menu;

import com.terrainlab.TerrainLab;
import com.terrainlab.blockentity.AncientCodexBlockEntity;
import com.terrainlab.data.CodexSavedData;
import com.terrainlab.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Menu for the Ancient Codex: a book, lapis and XP plus three selected glyphs produce an enchanted book
 * whose enchantment is looked up from the world's glyph combination table.
 */
public class AncientCodexMenu extends AbstractContainerMenu {

    private static final int SLOT_BOOK = 0;
    private static final int SLOT_LAPIS = 1;
    private static final int SLOT_OUTPUT = 2;
    private static final int XP_COST = 1; // levels

    private final AncientCodexBlockEntity blockEntity;

    /** Client-side copy of the selected-glyph bitmask (bit i = glyph i), synced from the server via a DataSlot. */
    private int syncedMask;

    public AncientCodexMenu(int containerId, Inventory playerInventory,
                            AncientCodexBlockEntity blockEntity) {
        super(ModMenuTypes.ANCIENT_CODEX_MENU.get(), containerId);
        this.blockEntity = blockEntity;

        addSlot(new Slot(blockEntity, SLOT_BOOK, 205, 74) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.BOOK);
            }
        });

        addSlot(new Slot(blockEntity, SLOT_LAPIS, 205, 97) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        });

        addSlot(new Slot(blockEntity, SLOT_OUTPUT, 119, 58) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Selected-glyph mask, server -> client.
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide()
                        ? syncedMask
                        : selectionMask();
            }

            @Override
            public void set(int value) {
                syncedMask = value;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        34 + col * 21, 135 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 34 + col * 21, 187));
        }
    }

    public AncientCodexMenu(int containerId, Inventory playerInventory,
                            FriendlyByteBuf buf) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, buf));
    }

    private static AncientCodexBlockEntity getBlockEntity(Inventory inv,
                                                          FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        return (AncientCodexBlockEntity) inv.player.level().getBlockEntity(pos);
    }

    /** Selected glyphs as a bitmask - correct on both sides. */
    public int getSelectedMask() {
        return blockEntity.getLevel() != null && blockEntity.getLevel().isClientSide()
                ? syncedMask
                : selectionMask();
    }

    /** Client only: toggles a glyph immediately for responsive highlighting; the next server sync overrides it. */
    public void predictToggle(int glyphIndex) {
        syncedMask ^= 1 << glyphIndex;
    }

    public void handleGlyphSelection(Player player, int glyphIndex) {
        boolean toggled = blockEntity.toggleGlyph(glyphIndex);
        // TerrainLab.LOGGER.info("Glyph {} toggled: {}, selected count: {}",
                // glyphIndex, toggled, blockEntity.getSelectedCount());
        if (blockEntity.getSelectedCount() == 3) {
            // TerrainLab.LOGGER.info("3 glyphs selected, attempting output");
            tryProduceOutput(player);
        }
    }

    /**
     * Tells the player's client how the attempt went, so the codex screen can
     * play the matching animation (glyphs fly into the output, or flicker and
     * dim). {@code mask} must be captured before resetGlyphs().
     */
    private static void sendResult(Player player, boolean success, int mask) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new com.terrainlab.network.CodexResultPacket(success, mask));
        }
    }

    private int selectionMask() {
        // Read the flags directly: getSelectedIndices() pads with 0, which would mark glyph 0 as selected.
        boolean[] selected = blockEntity.getSelectedGlyphs();
        int mask = 0;
        for (int i = 0; i < selected.length && i < 8; i++) {
            if (selected[i]) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    private void tryProduceOutput(Player player) {
        int mask = selectionMask();
        ItemStack book = blockEntity.getItem(SLOT_BOOK);
        ItemStack lapis = blockEntity.getItem(SLOT_LAPIS);

        if (book.isEmpty() || !book.is(Items.BOOK)) {
            // TerrainLab.LOGGER.info("No book in slot: {}", book);
            sendResult(player, false, mask);
            return;
        }
        if (lapis.isEmpty() || !lapis.is(Items.LAPIS_LAZULI)) {
            // TerrainLab.LOGGER.info("No lapis in slot: {}", lapis);
            sendResult(player, false, mask);
            return;
        }
        if (player.experienceLevel < XP_COST) {
            // TerrainLab.LOGGER.info("Not enough XP: {} < {}", player.experienceLevel, XP_COST);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("ancient_codex.not_enough_xp"),
                    true);
            blockEntity.resetGlyphs();
            sendResult(player, false, mask);
            return;
        }
        if (!blockEntity.getItem(SLOT_OUTPUT).isEmpty()) {
            // TerrainLab.LOGGER.info("Output slot not empty");
            sendResult(player, false, mask);
            return;
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            CodexSavedData savedData = CodexSavedData.get(serverLevel);
            int[] glyphs = blockEntity.getSelectedIndices();
            // TerrainLab.LOGGER.info("Looking up glyphs: {},{},{}",
                    // glyphs[0], glyphs[1], glyphs[2]);
            Identifier enchantmentId = savedData.getEnchantment(glyphs);
            // TerrainLab.LOGGER.info("Enchantment result: {}", enchantmentId);

            java.util.Optional<net.minecraft.core.Holder.Reference<net.minecraft.world.item.enchantment.Enchantment>> enchantment =
                    java.util.Optional.empty();
            if (enchantmentId != null) {
                enchantment = serverLevel.holderLookup(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .get(net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.ENCHANTMENT,
                                enchantmentId));
            }

            // Nothing is charged when the combination has no enchantment.
            if (enchantment.isEmpty()) {
                blockEntity.resetGlyphs();
                blockEntity.setChanged();
                sendResult(player, false, mask);
                return;
            }

            book.shrink(1);
            lapis.shrink(1);
            player.giveExperienceLevels(-XP_COST);

            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable mutable =
                    new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            mutable.set(enchantment.get(), 1);
            EnchantmentHelper.setEnchantments(enchantedBook,
                    mutable.toImmutable());
            blockEntity.setItem(SLOT_OUTPUT, enchantedBook);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "codex_enchant");
            }
            sendResult(player, true, mask);
        }

        blockEntity.resetGlyphs();
        blockEntity.setChanged();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }

    public AncientCodexBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
