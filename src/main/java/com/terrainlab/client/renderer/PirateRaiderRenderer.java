package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;

/**
 * Pillager renderer with the Pirate Raider texture; the bandana and eyepatch are painted
 * into the texture rather than added as geometry.
 */
public class PirateRaiderRenderer extends PillagerRenderer {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/raider/pirate_pillager.png");

    public PirateRaiderRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(IllagerRenderState state) {
        return TEXTURE;
    }
}
