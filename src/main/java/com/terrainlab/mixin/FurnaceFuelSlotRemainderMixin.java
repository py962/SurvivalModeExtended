package com.terrainlab.mixin;

import net.minecraft.world.inventory.FurnaceFuelSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players can put only one remainder-leaving fuel (a lava bucket) in the furnace fuel slot. */
@Mixin(FurnaceFuelSlot.class)
public abstract class FurnaceFuelSlotRemainderMixin {

    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void terrainlab$singleRemainderFuel(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!stack.getCraftingRemainder().isEmpty()) {
            cir.setReturnValue(1);
        }
    }
}
