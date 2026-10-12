package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;

/** Pillager renderer with the Trapper Raider texture; the fur hat is painted into the texture. */
public class TrapperRaiderRenderer extends PillagerRenderer {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/raider/trapper_pillager.png");

    public TrapperRaiderRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(IllagerRenderState state) {
        return TEXTURE;
    }
}
