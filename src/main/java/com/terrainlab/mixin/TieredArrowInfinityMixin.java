
        package com.terrainlab.mixin;

import com.terrainlab.item.TieredArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Allows Infinity bows to avoid consuming TerrainLab's custom tiered arrows.
 */
@Mixin(EnchantmentHelper.class)
public abstract class TieredArrowInfinityMixin {

    @Redirect(
            method = "getItemEnchantmentLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"
            ),
            require = 0
    )
    private static boolean terrainlab$allowInfinityForTieredArrows(
            ItemStack stack,
            net.minecraft.world.item.Item item) {
        return stack.is(item)
                || (item == net.minecraft.world.item.Items.ARROW
                && stack.getItem() instanceof TieredArrowItem);
    }
}


