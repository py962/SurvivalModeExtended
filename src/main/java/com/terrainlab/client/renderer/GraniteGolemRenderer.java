package com.terrainlab.client.renderer;

import com.terrainlab.client.model.GraniteGolemModel;
import com.terrainlab.entity.GraniteGolemEntity;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;


public class GraniteGolemRenderer extends MobRenderer<GraniteGolemEntity, GraniteGolemRenderState, GraniteGolemModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/entity/granite_golem.png");
    private static final Identifier TEXTURE_BLUE_EYES =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/entity/granite_golem_blue_eyes.png");

    public GraniteGolemRenderer(EntityRendererProvider.Context context) {
        super(context, new GraniteGolemModel(
                context.bakeLayer(ModModelLayers.GRANITE_GOLEM)), 0.7F);
    }

    @Override
    public GraniteGolemRenderState createRenderState() {
        return new GraniteGolemRenderState();
    }

    @Override
    public void extractRenderState(GraniteGolemEntity entity, GraniteGolemRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.isTunnelling = entity.getGolemState() == GraniteGolemEntity.STATE_TUNNELLING;
        state.eyesGlowing = entity.isEyesRed();
        state.animTick = entity.tickCount;
    }

    @Override
    public Identifier getTextureLocation(GraniteGolemRenderState state) {
        return state.eyesGlowing ? TEXTURE_BLUE_EYES : TEXTURE;
    }
}