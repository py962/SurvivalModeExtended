package com.terrainlab.mixin;

import com.terrainlab.registry.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trade costs carrying {@code REQUIRES_ENCHANTMENT} accept any enchanted copy of the item,
 * whatever its enchantments, instead of an exact component match.
 */
@Mixin(ItemCost.class)
public abstract class ItemCostEnchantedMixin {

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void terrainlab$requireEnchanted(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        ItemCost self = (ItemCost) (Object) this;
        if (self.itemStack().has(ModDataComponents.REQUIRES_ENCHANTMENT.get())) {
            cir.setReturnValue(stack.is(self.item()) && EnchantmentHelper.hasAnyEnchantments(stack));
        }
    }
}
