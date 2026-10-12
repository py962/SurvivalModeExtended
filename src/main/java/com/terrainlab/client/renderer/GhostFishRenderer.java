package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.model.GhostFishModel;
import com.terrainlab.entity.GhostFishEntity;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Renders the Ghost Fish translucent and self-lit. */
public class GhostFishRenderer extends MobRenderer<GhostFishEntity, GhostFishRenderState, GhostFishModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/ghost_fish.png");

    public GhostFishRenderer(EntityRendererProvider.Context context) {
        super(context, new GhostFishModel(context.bakeLayer(ModModelLayers.GHOST_FISH)), 0.15F);
    }

    @Override
    public GhostFishRenderState createRenderState() {
        return new GhostFishRenderState();
    }

    @Override
    public void extractRenderState(GhostFishEntity entity, GhostFishRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    @Override
    public Identifier getTextureLocation(GhostFishRenderState state) {
        return TEXTURE;
    }

    // Glow-squid style emissive translucency: unaffected by ambient darkness (casts no light).
    @Override
    protected RenderType getRenderType(GhostFishRenderState state, boolean isVisible,
                                       boolean isVisibleToPlayer, boolean isGlowing) {
        return RenderTypes.entityTranslucentEmissive(getTextureLocation(state));
    }
}
