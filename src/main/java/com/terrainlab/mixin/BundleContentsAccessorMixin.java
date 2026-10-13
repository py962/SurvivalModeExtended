package com.terrainlab.mixin;

import com.mojang.serialization.DataResult;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Supplier;

@Mixin(BundleContents.class)
public interface BundleContentsAccessorMixin {

    @Invoker("getWeight")
    static DataResult<Fraction> terrainlab$getWeight(ItemInstance item) {
        throw new AssertionError();
    }

    /** 26.x computes an immutable BundleContents' weight lazily; this lets the tooltip carry the Overstacking weight. */
    @Mutable
    @Accessor("weight")
    void terrainlab$setWeight(Supplier<DataResult<Fraction>> weight);
}
