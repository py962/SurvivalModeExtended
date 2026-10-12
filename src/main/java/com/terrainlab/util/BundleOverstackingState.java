package com.terrainlab.util;

import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Thread-local flag set while BundleItem operates on a bundle with Overstacking; BundleContents.Mutable
 * reads it on construction to treat each contained item's max stack size as 64.
 */
public final class BundleOverstackingState {

    private static final ThreadLocal<Boolean> ACTIVE =
            ThreadLocal.withInitial(() -> false);

    private BundleOverstackingState() {
    }

    public static boolean isActive() {
        return ACTIVE.get();
    }

    public static void set(boolean overstacking) {
        ACTIVE.set(overstacking);
    }

    public static void clear() {
        ACTIVE.remove();
    }

    /** True when the bundle item itself (not its contents) carries Overstacking. */
    public static boolean hasOverstacking(ItemStack bundleStack) {
        ItemEnchantments enchantments = bundleStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(ModEnchantments.OVERSTACKING) && enchantments.getLevel(holder) > 0) {
                return true;
            }
        }
        return false;
    }
}
