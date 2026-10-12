package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.GooseEntity;
import com.terrainlab.client.model.GooseModel;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.model.animal.parrot.ParrotModel;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.ParrotRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders the goose with {@link GooseModel}, reusing ParrotRenderState for pose and flap
 * angle. Extends MobRenderer because ParrotRenderer is hard-typed to ParrotModel.
 */
public class GooseRenderer extends MobRenderer<GooseEntity, ParrotRenderState, GooseModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/goose.png");

    public GooseRenderer(EntityRendererProvider.Context context) {
        super(context, new GooseModel(context.bakeLayer(ModModelLayers.GOOSE)), 0.3F);
    }

    @Override
    public ParrotRenderState createRenderState() {
        return new ParrotRenderState();
    }

    @Override
    public void extractRenderState(GooseEntity entity, ParrotRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        // Pose and a one-beat-per-second flap; folded wings when perched or still.
        BirdFlapping.apply(entity, state, partialTick);
    }

    @Override
    public Identifier getTextureLocation(ParrotRenderState state) {
        return TEXTURE;
    }
}
