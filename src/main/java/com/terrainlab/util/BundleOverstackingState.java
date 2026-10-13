package com.terrainlab.util;

import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;

import java.util.List;
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
    public static boolean hasOverstacking(ItemInstance bundleStack) {
        ItemEnchantments enchantments = bundleStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(ModEnchantments.OVERSTACKING) && enchantments.getLevel(holder) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Total Overstacking weight of a list of bundle contents (each item weighed by {@link #overstackWeight}). */
    public static Fraction overstackContentsWeight(List<? extends ItemInstance> items) {
        Fraction total = Fraction.ZERO;
        for (ItemInstance stack : items) {
            total = total.add(overstackWeight(stack).multiplyBy(Fraction.getFraction(stack.count(), 1)));
        }
        return total;
    }

    /**
     * Weight of one item in an Overstacking bundle: 1/64 regardless of its stack size. A nested
     * bundle weighs 1/16 plus its contents, as in vanilla, so nesting cannot give unlimited
     * storage; an inner Overstacking bundle is weighed by its overstacked contents.
     */
    public static Fraction overstackWeight(ItemInstance stack) {
        BundleContents inner = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (inner == null) {
            return Fraction.getFraction(1, 64);
        }
        Fraction innerWeight = hasOverstacking(stack)
                ? overstackContentsWeight(inner.items())
                : inner.weight().result().orElse(Fraction.ONE);
        return Fraction.getFraction(1, 16).add(innerWeight);
    }
}
