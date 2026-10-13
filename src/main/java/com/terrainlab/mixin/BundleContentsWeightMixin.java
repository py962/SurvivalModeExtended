package com.terrainlab.mixin;

import com.mojang.serialization.DataResult;
import com.terrainlab.util.BundleOverstackingState;
import net.minecraft.world.item.ItemInstance;
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
    private Fraction weight;

    // 26.x: BundleContents#asMutable() goes through this private constructor with the vanilla weight.
    @Inject(
            method = "<init>(Ljava/util/List;Lorg/apache/commons/lang3/math/Fraction;I)V",
            at = @At("RETURN")
    )
    private void terrainlab$recalculateOverstackingWeight(
            List<ItemStack> items,
            Fraction initialWeight,
            int selectedItem,
            CallbackInfo ci
    ) {
        if (!BundleOverstackingState.isActive()) {
            return;
        }

        this.weight = BundleOverstackingState.overstackContentsWeight(items);
    }

    @Redirect(
            method = {
                    "tryInsert",
                    "tryTransfer"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/BundleContents;getWeight(Lnet/minecraft/world/item/ItemInstance;)Lcom/mojang/serialization/DataResult;"
            )
    )
    private DataResult<Fraction> terrainlab$overstackingItemWeight(ItemInstance stack) {
        return terrainlab$itemWeight(stack);
    }

    // Static callers: removeOne/setItem go through getStackedWeight(ItemStack); slot edits through getWeightWithAddedItems.
    @Redirect(
            method = {
                    "getStackedWeight(Lnet/minecraft/world/item/ItemStack;)Lorg/apache/commons/lang3/math/Fraction;",
                    "getWeightWithAddedItems"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/BundleContents;getWeight(Lnet/minecraft/world/item/ItemInstance;)Lcom/mojang/serialization/DataResult;"
            )
    )
    private static DataResult<Fraction> terrainlab$overstackingItemWeightStatic(ItemInstance stack) {
        return terrainlab$itemWeight(stack);
    }

    @Unique
    private static DataResult<Fraction> terrainlab$itemWeight(ItemInstance stack) {
        if (BundleOverstackingState.isActive()) {
            return DataResult.success(BundleOverstackingState.overstackWeight(stack));
        }

        return BundleContentsAccessorMixin.terrainlab$getWeight(stack);
    }
}
