package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PolarBearRenderer;
import net.minecraft.client.renderer.entity.state.PolarBearRenderState;
import net.minecraft.resources.Identifier;

/** Polar bear renderer with the grizzly bear texture. */
public class GrizzlyBearRenderer extends PolarBearRenderer {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/grizzly_bear.png");

    public GrizzlyBearRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(PolarBearRenderState state) {
        return TEXTURE;
    }
}
