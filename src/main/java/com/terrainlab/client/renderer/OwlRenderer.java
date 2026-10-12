package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.OwlEntity;
import com.terrainlab.client.model.OwlModel;
import com.terrainlab.registry.ModModelLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the owl with {@link OwlModel}, reusing ParrotRenderState for pose and flap
 * angle. Extends MobRenderer because ParrotRenderer is hard-typed to ParrotModel.
 */
public class OwlRenderer extends MobRenderer<OwlEntity, ParrotRenderState, OwlModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/owl.png");

    public OwlRenderer(EntityRendererProvider.Context context) {
        super(context, new OwlModel(context.bakeLayer(ModModelLayers.OWL)), 0.3F);
    }

    @Override
    public ParrotRenderState createRenderState() {
        return new ParrotRenderState();
    }

    @Override
    public void extractRenderState(OwlEntity entity, ParrotRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // Pose and a one-beat-per-second flap; folded wings when perched or still.
        BirdFlapping.apply(entity, state, partialTick);
    }

    @Override
    public Identifier getTextureLocation(ParrotRenderState state) {
        return TEXTURE;
    }

}
