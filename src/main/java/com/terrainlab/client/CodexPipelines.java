package com.terrainlab.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Custom GUI render pipelines, registered in TerrainLabClient#onRegisterRenderPipelines. */
public final class CodexPipelines {

    private CodexPipelines() {
    }

    /**
     * Textured GUI pipeline with additive blending (SRC_ALPHA, ONE), so sprites add light
     * to what is underneath and read as a glow. The blit colour/alpha scale the added light.
     */
    public static final RenderPipeline GUI_GLOW = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("terrainlab", "pipeline/gui_glow"))
            .withBlend(BlendFunction.LIGHTNING)
            .build();
}
