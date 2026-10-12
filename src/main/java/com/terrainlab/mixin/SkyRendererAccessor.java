package com.terrainlab.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes LevelRenderer's private SkyRenderer. */
@Mixin(LevelRenderer.class)
public interface SkyRendererAccessor {
    @Accessor("skyRenderer")
    SkyRenderer getSkyRenderer();
}
