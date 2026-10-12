package com.terrainlab.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BundleContents.class)
public interface BundleContentsAccessorMixin {

    @Invoker("getWeight")
    static Fraction terrainlab$getWeight(ItemStack stack) {
        throw new AssertionError();
    }
}
