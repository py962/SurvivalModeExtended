package com.terrainlab.mixin;

import com.mojang.serialization.DataResult;
import com.terrainlab.registry.ModEnchantments;
import com.terrainlab.util.BundleOverstackingState;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.BundleTooltip;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Overstacking support for bundles. Sets {@link BundleOverstackingState} around the click handlers
 * so inserts and removals use Overstacking weights, and recomputes the weight for the item bar
 * and tooltip, because the weight stored on a network- or codec-decoded {@code BundleContents} is
 * always the vanilla one.
 */
@Mixin(BundleItem.class)
public class BundleItemOverstackingMixin {

    // Mirrors BundleItem's private FULL_BAR_COLOR / BAR_COLOR.
    @Unique
    private static final int TERRAINLAB$FULL_BAR_COLOR = ARGB.colorFromFloat(1.0F, 1.0F, 0.33F, 0.33F);

    @Unique
    private static final int TERRAINLAB$BAR_COLOR = ARGB.colorFromFloat(1.0F, 0.44F, 0.53F, 1.0F);

    @Inject(method = "getFullnessDisplay", at = @At("HEAD"), cancellable = true)
    private static void terrainlab$fullnessDisplayOverstacking(
            ItemStack bundleStack,
            CallbackInfoReturnable<Float> cir
    ) {
        if (!terrainlab$hasOverstacking(bundleStack)) {
            return;
        }
        cir.setReturnValue(terrainlab$effectiveWeight(bundleStack).floatValue());
    }

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    private void terrainlab$barVisibleOverstacking(
            ItemStack bundleStack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!terrainlab$hasOverstacking(bundleStack)) {
            return;
        }
        cir.setReturnValue(terrainlab$effectiveWeight(bundleStack).compareTo(Fraction.ZERO) > 0);
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    private void terrainlab$barWidthOverstacking(
            ItemStack bundleStack,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!terrainlab$hasOverstacking(bundleStack)) {
            return;
        }
        cir.setReturnValue(Math.min(1 + Mth.mulAndTruncate(terrainlab$effectiveWeight(bundleStack), 12), 13));
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    private void terrainlab$barColorOverstacking(
            ItemStack bundleStack,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!terrainlab$hasOverstacking(bundleStack)) {
            return;
        }
        cir.setReturnValue(
                terrainlab$effectiveWeight(bundleStack).compareTo(Fraction.ONE) >= 0
                        ? TERRAINLAB$FULL_BAR_COLOR
                        : TERRAINLAB$BAR_COLOR
        );
    }

    /** Hands the tooltip a copy of the contents with the Overstacking weight recalculated. */
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void terrainlab$tooltipOverstacking(
            ItemStack bundleStack,
            CallbackInfoReturnable<Optional<TooltipComponent>> cir
    ) {
        if (!terrainlab$hasOverstacking(bundleStack)) {
            return;
        }
        BundleContents contents = bundleStack.get(DataComponents.BUNDLE_CONTENTS);
        TooltipDisplay display = bundleStack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (contents == null || !display.shows(DataComponents.BUNDLE_CONTENTS)) {
            return;
        }
        // 26.x: an immutable BundleContents computes its weight lazily with vanilla weights, so the
        // copy's weight is overridden with the Overstacking total.
        // asMutable() keeps the selected item but returns an empty container if the vanilla weight overflowed.
        BundleContents copy = contents.weight().isError()
                ? new BundleContents(contents.items())
                : contents.asMutable().toImmutable();
        Fraction overstackingWeight = BundleOverstackingState.overstackContentsWeight(contents.items());
        ((BundleContentsAccessorMixin) (Object) copy).terrainlab$setWeight(() -> DataResult.success(overstackingWeight));
        cir.setReturnValue(Optional.of(new BundleTooltip(copy)));
    }

    /** True when the bundle item itself, not its contents, has Overstacking. */
    private static boolean terrainlab$hasOverstacking(ItemStack bundleStack) {
        return BundleOverstackingState.hasOverstacking(bundleStack);
    }

    /**
     * Recomputes the bundle's Overstacking weight from its contents, ignoring the
     * possibly vanilla-computed weight stored on the component.
     */
    private static Fraction terrainlab$effectiveWeight(ItemStack bundleStack) {
        BundleContents contents = bundleStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return BundleOverstackingState.overstackContentsWeight(contents.items());
    }

    @Inject(
            method = "overrideStackedOnOther",
            at = @At("HEAD")
    )
    private void terrainlab$startOverstacking1(
            ItemStack bundleStack,
            Slot slot,
            ClickAction action,
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        terrainlab$applyFlag(bundleStack, player);
    }

    @Inject(
            method = "overrideStackedOnOther",
            at = @At("RETURN")
    )
    private void terrainlab$endOverstacking1(
            ItemStack bundleStack,
            Slot slot,
            ClickAction action,
            Player player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BundleOverstackingState.clear();
    }

    @Inject(
            method = "overrideOtherStackedOnMe",
            at = @At("HEAD")
    )
    private void terrainlab$startOverstacking2(
            ItemStack bundleStack,
            ItemStack other,
            Slot slot,
            ClickAction action,
            Player player,
            SlotAccess slotAccess,
            CallbackInfoReturnable<Boolean> cir
    ) {
        terrainlab$applyFlag(bundleStack, player);
    }

    @Inject(
            method = "overrideOtherStackedOnMe",
            at = @At("RETURN")
    )
    private void terrainlab$endOverstacking2(
            ItemStack bundleStack,
            ItemStack other,
            Slot slot,
            ClickAction action,
            Player player,
            SlotAccess slotAccess,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BundleOverstackingState.clear();
    }

    @Inject(
            method = "removeOneItemFromBundle",
            at = @At("HEAD")
    )
    private static void terrainlab$startOverstacking3(
            ItemStack bundleStack,
            Player player,
            BundleContents contents,
            CallbackInfoReturnable<Optional<ItemStack>> cir
    ) {
        terrainlab$applyFlag(bundleStack, player);
    }

    @Inject(
            method = "removeOneItemFromBundle",
            at = @At("RETURN")
    )
    private static void terrainlab$endOverstacking3(
            ItemStack bundleStack,
            Player player,
            BundleContents contents,
            CallbackInfoReturnable<Optional<ItemStack>> cir
    ) {
        BundleOverstackingState.clear();
    }

    private static void terrainlab$applyFlag(
            ItemStack bundleStack,
            Player player
    ) {
        Holder<Enchantment> overstacking =
                ModEnchantments.holder(
                        player.registryAccess(),
                        ModEnchantments.OVERSTACKING
                );

        boolean active =
                EnchantmentHelper.getItemEnchantmentLevel(
                        overstacking,
                        bundleStack
                ) > 0;

        BundleOverstackingState.set(active);
    }
}
