package com.terrainlab.blockentity;

import com.terrainlab.entity.TieredArrowEntity;
import com.terrainlab.menu.FletchingTableMenu;
import com.terrainlab.registry.ModBlockEntities;
import com.terrainlab.registry.ModDataComponents;
import com.terrainlab.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Fletching table inventory: feather, stick, flint and an optional potion or stew produce a preview stack of tiered arrows. */
public class FletchingTableBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int SLOT_FEATHER = 0;
    public static final int SLOT_STICK = 1;
    public static final int SLOT_FLINT = 2;
    public static final int SLOT_POTION = 3; // optional potion or suspicious stew
    public static final int SLOT_OUTPUT = 4;

    private static final int SLOT_COUNT = 5;
    private static final int OUTPUT_COUNT = 8;

    private final ItemStack[] items = new ItemStack[SLOT_COUNT];

    public FletchingTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLETCHING_TABLE.get(), pos, state);
        Arrays.fill(this.items, ItemStack.EMPTY);
    }

    public static boolean isValidFeather(ItemStack stack) {
        return isFeatherItem(stack);
    }

    private static boolean isFeatherItem(ItemStack stack) {
        Item item = stack.getItem();

        return item == Items.FEATHER
                || item == ModItems.OWL_FEATHER.get()
                || item == ModItems.PARROT_FEATHER.get()
                || item == ModItems.VULTURE_FEATHER.get()
                || item == ModItems.GOOSE_FEATHER.get();
    }

    public static boolean isValidStick(ItemStack stack) {
        return stack.is(Items.STICK);
    }

    public static boolean isValidFlint(ItemStack stack) {
        return stack.is(Items.FLINT);
    }

    /**
     * Whether a stack may go in the optional effect slot: empty, any potion
     * variant with contents, or a suspicious stew with exactly one effect.
     */
    public static boolean isValidPotion(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }

        if (stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION)) {

            return stack.has(DataComponents.POTION_CONTENTS);
        }

        if (stack.is(Items.SUSPICIOUS_STEW)) {
            return getSupportedStewEffect(stack) != null;
        }

        return false;
    }

    /**
     * The stew's effect if it has exactly one, otherwise null. Any stew works; the arrow applies
     * the effect for the stew's own duration (normal-tier arrows apply potion contents in full).
     */
    private static SuspiciousStewEffects.Entry getSupportedStewEffect(ItemStack stew) {
        SuspiciousStewEffects effects =
                stew.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);

        if (effects == null || effects.effects().size() != 1) {
            return null;
        }

        return effects.effects().getFirst();
    }

    public void updateOutput() {
        ItemStack feather = this.items[SLOT_FEATHER];
        ItemStack stick = this.items[SLOT_STICK];
        ItemStack flint = this.items[SLOT_FLINT];
        ItemStack potion = this.items[SLOT_POTION];

        if (!isFeatherItem(feather)
                || stick.isEmpty()
                || !isValidStick(stick)
                || flint.isEmpty()
                || !isValidFlint(flint)) {

            this.items[SLOT_OUTPUT] = ItemStack.EMPTY;
            return;
        }

        if (!isValidPotion(potion)) {
            this.items[SLOT_OUTPUT] = ItemStack.EMPTY;
            return;
        }

        TieredArrowEntity.FeatherType featherType = resolveFeatherType(feather);
        Item arrowItem = resolveArrowItem(featherType);

        ItemStack output = new ItemStack(arrowItem, OUTPUT_COUNT);

        if (!potion.isEmpty()) {

            if (potion.has(DataComponents.POTION_CONTENTS)) {
                PotionContents contents =
                        potion.get(DataComponents.POTION_CONTENTS);

                if (contents != null) {
                    output.set(DataComponents.POTION_CONTENTS, contents);
                    output.set(
                            ModDataComponents.ARROW_TIER.get(),
                            resolveTier(potion)
                    );
                }
            }

            // Stew effects become PotionContents so TieredArrowEntity applies them unchanged.
            else if (potion.is(Items.SUSPICIOUS_STEW)) {
                SuspiciousStewEffects.Entry stewEffect =
                        getSupportedStewEffect(potion);

                if (stewEffect != null) {
                    PotionContents contents = new PotionContents(
                            Optional.empty(),
                            Optional.empty(),
                            List.of(stewEffect.createEffectInstance()),
                            Optional.empty()
                    );

                    output.set(DataComponents.POTION_CONTENTS, contents);
                    output.set(
                            ModDataComponents.ARROW_TIER.get(),
                            TieredArrowEntity.PotionTier.NORMAL
                    );
                }
            }

        } else {
            output.set(
                    ModDataComponents.ARROW_TIER.get(),
                    TieredArrowEntity.PotionTier.NONE
            );
        }

        this.items[SLOT_OUTPUT] = output;
    }

    private static TieredArrowEntity.FeatherType resolveFeatherType(ItemStack feather) {
        if (feather.is(ModItems.OWL_FEATHER.get())) {
            return TieredArrowEntity.FeatherType.OWL;
        }

        if (feather.is(ModItems.PARROT_FEATHER.get())) {
            return TieredArrowEntity.FeatherType.PARROT;
        }

        if (feather.is(ModItems.VULTURE_FEATHER.get())) {
            return TieredArrowEntity.FeatherType.VULTURE;
        }

        if (feather.is(ModItems.GOOSE_FEATHER.get())) {
            return TieredArrowEntity.FeatherType.GOOSE;
        }

        return TieredArrowEntity.FeatherType.NORMAL;
    }

    private static Item resolveArrowItem(
            TieredArrowEntity.FeatherType featherType) {

        return switch (featherType) {
            case OWL -> ModItems.OWL_TIERED_ARROW.get();
            case PARROT -> ModItems.PARROT_TIERED_ARROW.get();
            case VULTURE -> ModItems.VULTURE_TIERED_ARROW.get();
            case GOOSE -> ModItems.GOOSE_TIERED_ARROW.get();
            default -> ModItems.NORMAL_TIERED_ARROW.get();
        };
    }

    private static TieredArrowEntity.PotionTier resolveTier(ItemStack potion) {
        if (potion.is(Items.SPLASH_POTION)) {
            return TieredArrowEntity.PotionTier.SPLASH;
        }

        if (potion.is(Items.LINGERING_POTION)) {
            return TieredArrowEntity.PotionTier.LINGERING;
        }

        return TieredArrowEntity.PotionTier.NORMAL;
    }

    public void consumeInputs() {
        this.items[SLOT_FEATHER].shrink(1);
        this.items[SLOT_STICK].shrink(1);
        this.items[SLOT_FLINT].shrink(1);

        if (!this.items[SLOT_POTION].isEmpty()) {
            this.items[SLOT_POTION].shrink(1);
        }

        updateOutput();
        setChanged();
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = net.minecraft.world.ContainerHelper.removeItem(
                Arrays.asList(this.items),
                slot,
                amount
        );

        if (!result.isEmpty()) {
            updateOutput();
        }

        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = this.items[slot];
        this.items[slot] = ItemStack.EMPTY;
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items[slot] = stack;

        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        if (slot != SLOT_OUTPUT) {
            updateOutput();
        }

        setChanged();
    }

    /** Hoppers may insert valid ingredients but never touch the output preview. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_FEATHER -> isValidFeather(stack);
            case SLOT_STICK -> isValidStick(stack);
            case SLOT_FLINT -> isValidFlint(stack);
            case SLOT_POTION -> !stack.isEmpty() && isValidPotion(stack);
            default -> false;
        };
    }

    /** The output is a preview built from the inputs; only the menu's take path may remove it. */
    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return slot != SLOT_OUTPUT;
    }

    /** Drops the inputs but not the output preview when the table is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        this.items[SLOT_OUTPUT] = ItemStack.EMPTY;
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        NonNullList<ItemStack> inputs = NonNullList.withSize(SLOT_OUTPUT, ItemStack.EMPTY);
        for (int i = 0; i < SLOT_OUTPUT; i++) {
            inputs.set(i, this.items[i]);
        }
        ContainerHelper.saveAllItems(output, inputs);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        NonNullList<ItemStack> inputs = NonNullList.withSize(SLOT_OUTPUT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, inputs);
        for (int i = 0; i < SLOT_OUTPUT; i++) {
            this.items[i] = inputs.get(i);
        }
        updateOutput();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null
                || this.level.getBlockEntity(this.worldPosition) != this) {

            return false;
        }

        return player.distanceToSqr(
                this.worldPosition.getX() + 0.5,
                this.worldPosition.getY() + 0.5,
                this.worldPosition.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public void clearContent() {
        Arrays.fill(this.items, ItemStack.EMPTY);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(
                "container.terrainlab.fletching_table"
        );
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(
            int containerId,
            Inventory inventory,
            Player player) {

        return new FletchingTableMenu(
                containerId,
                inventory,
                this
        );
    }
}
