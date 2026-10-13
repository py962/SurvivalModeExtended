package com.terrainlab.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terrainlab.client.model.HouseboatModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import static com.terrainlab.TerrainLab.MODID;


public class HouseboatRenderer extends AbstractBoatRenderer {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(MODID, "textures/entity/houseboat.png");

    private final EntityModel<BoatRenderState> model;
    private final Model.Simple waterPatchModel;

    public HouseboatRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE);
        this.model = new HouseboatModel(
                context.bakeLayer(com.terrainlab.registry.ModModelLayers.HOUSEBOAT));
        this.waterPatchModel = new Model.Simple(
                context.bakeLayer(com.terrainlab.registry.ModModelLayers.HOUSEBOAT_WATER_PATCH),
                p -> RenderTypes.waterMask());
    }

    @Override
    protected EntityModel<BoatRenderState> model() {
        return model;
    }

    @Override
    protected void submitTypeAdditions(BoatRenderState state, PoseStack poseStack,
                                       SubmitNodeCollector collector, int lightCoords) {
        if (!state.isUnderWater) {
            collector.submitModel(waterPatchModel, Unit.INSTANCE, poseStack,
                    TEXTURE,
                    lightCoords, OverlayTexture.NO_OVERLAY,
                    state.outlineColor);
        }
    }
}


