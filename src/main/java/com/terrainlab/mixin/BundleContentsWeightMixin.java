package com.terrainlab.mixin;

import com.terrainlab.util.BundleOverstackingState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Applies Overstacking weights to {@code BundleContents.Mutable} while
 * {@link BundleOverstackingState} is active: recomputes the total on construction and swaps the
 * per-item weight used when adding or removing items.
 */
@Mixin(BundleContents.Mutable.class)
public class BundleContentsWeightMixin {

    @Shadow
    private List<ItemStack> items;

    @Shadow
    private Fraction weight;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void terrainlab$recalculateOverstackingWeight(
            BundleContents contents,
            CallbackInfo ci
    ) {
        if (!BundleOverstackingState.isActive()) {
            return;
        }

        Fraction overstackingWeight = Fraction.ZERO;

        for (ItemStack stack : this.items) {
            overstackingWeight = overstackingWeight.add(
                    terrainlab$overstackWeight(stack).multiplyBy(Fraction.getFraction(stack.getCount(), 1))
            );
        }

        this.weight = overstackingWeight;
    }

    @Redirect(
            method = {
                    "getMaxAmountToAdd",
                    "tryInsert",
                    "removeOne"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/BundleContents;getWeight(Lnet/minecraft/world/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;"
            )
    )
    private Fraction terrainlab$overstackingItemWeight(ItemStack stack) {
        if (BundleOverstackingState.isActive()) {
            return terrainlab$overstackWeight(stack);
        }

        return BundleContentsAccessorMixin.terrainlab$getWeight(stack);
    }

    /**
     * Weight of one item in an Overstacking bundle: 1/64 regardless of its stack size. A nested
     * bundle weighs 1/16 plus its contents, as in vanilla, so nesting cannot give unlimited
     * storage; an inner Overstacking bundle is weighed by its overstacked contents.
     */
    @Unique
    private static Fraction terrainlab$overstackWeight(ItemStack stack) {
        BundleContents inner = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (inner == null) {
            return Fraction.getFraction(1, 64);
        }
        Fraction innerWeight = BundleOverstackingState.hasOverstacking(stack)
                ? new BundleContents.Mutable(inner).weight()
                : inner.weight();
        return Fraction.getFraction(1, 16).add(innerWeight);
    }
}
