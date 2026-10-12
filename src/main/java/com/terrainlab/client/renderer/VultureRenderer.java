package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.VultureEntity;
import com.terrainlab.client.model.VultureModel;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the vulture with {@link VultureModel}, reusing ParrotRenderState for pose and
 * flap angle. Extends MobRenderer because ParrotRenderer is hard-typed to ParrotModel.
 */
public class VultureRenderer extends MobRenderer<VultureEntity, ParrotRenderState, VultureModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/vulture.png");

    public VultureRenderer(EntityRendererProvider.Context context) {
        super(context, new VultureModel(context.bakeLayer(ModModelLayers.VULTURE)), 0.3F);
    }

    @Override
    public ParrotRenderState createRenderState() {
        return new ParrotRenderState();
    }

    @Override
    public void extractRenderState(VultureEntity entity, ParrotRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // Pose and a one-beat-per-second flap; folded wings when perched or still.
        BirdFlapping.apply(entity, state, partialTick);
    }

    @Override
    public Identifier getTextureLocation(ParrotRenderState state) {
        return TEXTURE;
    }
}
