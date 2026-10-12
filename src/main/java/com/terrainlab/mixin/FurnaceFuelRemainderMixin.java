package com.terrainlab.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops hoppers stacking fuel that leaves a remainder (a stacked lava bucket). The furnace
 * replaces the whole fuel slot with the remainder, so a stack would lose all but one bucket.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceFuelRemainderMixin {

    private static final int SLOT_FUEL = 1;

    @Shadow
    protected NonNullList<ItemStack> items;

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void terrainlab$singleRemainderFuel(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == SLOT_FUEL && !stack.getCraftingRemainder().isEmpty()
                && (stack.getCount() > 1 || !this.items.get(SLOT_FUEL).isEmpty())) {
            cir.setReturnValue(false);
        }
    }
}
