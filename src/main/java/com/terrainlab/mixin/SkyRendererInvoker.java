package com.terrainlab.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes SkyRenderer's private End sky / End flash draw calls (public before 26.x). */
@Mixin(SkyRenderer.class)
public interface SkyRendererInvoker {
    @Invoker("renderEndSky")
    void terrainlab$renderEndSky(RenderPass renderPass);

    @Invoker("renderEndFlash")
    void terrainlab$renderEndFlash(RenderPass renderPass, PoseStack poseStack, float intensity, float xAngle, float yAngle);
}
