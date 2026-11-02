package com.terrainlab.client.cushion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terrainlab.TerrainLab;
import com.terrainlab.cushion.CushionEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

import java.util.EnumMap;

public class CushionRenderer extends EntityRenderer<CushionEntity, CushionRenderState> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "cushion"), "main");

    private static final EnumMap<DyeColor, Identifier> TEXTURES = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            TEXTURES.put(color, Identifier.fromNamespaceAndPath(TerrainLab.MODID,
                    "textures/entity/cushion/" + color.getName() + "_cushion.png"));
        }
    }

    static Identifier texture(DyeColor color) {
        return TEXTURES.get(color);
    }

    private final CushionModel model;

    public CushionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new CushionModel(context.bakeLayer(LAYER));
    }

    @Override
    public void extractRenderState(CushionEntity cushion, CushionRenderState state, float partialTicks) {
        super.extractRenderState(cushion, state, partialTicks);
        state.direction = Direction.fromYRot(cushion.getYRot());
        state.texture = texture(cushion.getColor());
    }

    @Override
    public void submit(CushionRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.direction.toYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.translate(0.0, -0.25, 0.0);
        collector.submitModel(this.model, state, poseStack, this.model.renderType(state.texture),
                state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public CushionRenderState createRenderState() {
        return new CushionRenderState();
    }
}
