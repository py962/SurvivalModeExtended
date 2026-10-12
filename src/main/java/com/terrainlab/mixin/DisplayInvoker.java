package com.terrainlab.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes Display's private setGlowColorOverride(int), used to tint ore-marker glow outlines by ore type. */
@Mixin(Display.class)
public interface DisplayInvoker {

    @Invoker("setGlowColorOverride")
    void terrainlab$setGlowColorOverride(int rgb);
}
