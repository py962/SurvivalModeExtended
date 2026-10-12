package com.terrainlab.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terrainlab.blockentity.CelestialConduitBlockEntity;
import com.terrainlab.celestial.CelestialVariant;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Renders Celestial Conduits with vanilla's conduit models, materials and animation,
 * tinting every part with the variant's {@link CelestialVariant#tintColor()} instead of
 * white. No extra model or texture assets are needed.
 */

public class CelestialConduitRenderer implements BlockEntityRenderer<CelestialConduitBlockEntity, CelestialConduitRenderState> {

    private final MaterialSet materials;
    private final ModelPart eye;
    private final ModelPart wind;
    private final ModelPart shell;
    private final ModelPart cage;

    public CelestialConduitRenderer(BlockEntityRendererProvider.Context context) {
        this.materials = context.materials();
        this.eye = context.bakeLayer(ModelLayers.CONDUIT_EYE);
        this.wind = context.bakeLayer(ModelLayers.CONDUIT_WIND);
        this.shell = context.bakeLayer(ModelLayers.CONDUIT_SHELL);
        this.cage = context.bakeLayer(ModelLayers.CONDUIT_CAGE);
    }

    @Override
    public CelestialConduitRenderState createRenderState() {
        return new CelestialConduitRenderState();
    }

    @Override
    public void extractRenderState(CelestialConduitBlockEntity blockEntity, CelestialConduitRenderState state,
                                    float partialTick, Vec3 cameraPos,
                                    ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, cameraPos, crumblingOverlay);
        state.isActive = blockEntity.isActive();
        state.activeRotation = blockEntity.getActiveRotation(blockEntity.isActive() ? partialTick : 0.0F);
        state.animTime = blockEntity.tickCount + partialTick;
        state.animationPhase = blockEntity.tickCount / 66 % 3;
        // Vanilla opens the eye only while hunting; here it opens at full power (full frame),
        // since only the Breath variant actually hunts.
        state.isFullPower = blockEntity.isFullPower();
        CelestialVariant variant = blockEntity.variant();
        state.tintColor = variant != null ? variant.tintColor() : 0xFFFFFF;
    }

    @Override
    public void submit(CelestialConduitRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                        CameraRenderState cameraState) {
        int tint = 0xFF000000 | state.tintColor;

        if (!state.isActive) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(new Quaternionf().rotationY(state.activeRotation * (float) (Math.PI / 180.0)));
            collector.submitModelPart(
                    this.shell,
                    poseStack,
                    ConduitRenderer.SHELL_TEXTURE.renderType(RenderTypes::entitySolid),
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    this.materials.get(ConduitRenderer.SHELL_TEXTURE),
                    tint,
                    state.breakProgress
            );
            poseStack.popPose();
            return;
        }

        float f = state.activeRotation * (180.0F / (float) Math.PI);
        float f1 = Mth.sin(state.animTime * 0.1F) / 2.0F + 0.5F;
        f1 = f1 * f1 + f1;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.3F + f1 * 0.2F, 0.5F);
        Vector3f axis = new Vector3f(0.5F, 1.0F, 0.5F).normalize();
        poseStack.mulPose(new Quaternionf().rotationAxis(f * (float) (Math.PI / 180.0), axis));
        collector.submitModelPart(
                this.cage,
                poseStack,
                ConduitRenderer.ACTIVE_SHELL_TEXTURE.renderType(RenderTypes::entityCutoutNoCull),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                this.materials.get(ConduitRenderer.ACTIVE_SHELL_TEXTURE),
                tint,
                state.breakProgress
        );
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (state.animationPhase == 1) {
            poseStack.mulPose(new Quaternionf().rotationX((float) (Math.PI / 2)));
        } else if (state.animationPhase == 2) {
            poseStack.mulPose(new Quaternionf().rotationZ((float) (Math.PI / 2)));
        }

        Material windMaterial = state.animationPhase == 1 ? ConduitRenderer.VERTICAL_WIND_TEXTURE : ConduitRenderer.WIND_TEXTURE;
        var windRenderType = windMaterial.renderType(RenderTypes::entityCutoutNoCull);
        var windSprite = this.materials.get(windMaterial);
        collector.submitModelPart(this.wind, poseStack, windRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY,
                windSprite, tint, state.breakProgress);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(0.875F, 0.875F, 0.875F);
        poseStack.mulPose(new Quaternionf().rotationXYZ((float) Math.PI, 0.0F, (float) Math.PI));
        collector.submitModelPart(this.wind, poseStack, windRenderType, state.lightCoords, OverlayTexture.NO_OVERLAY,
                windSprite, tint, state.breakProgress);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.3F + f1 * 0.2F, 0.5F);
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(cameraState.orientation);
        poseStack.mulPose(new Quaternionf().rotationZ((float) Math.PI).rotateY((float) Math.PI));
        poseStack.scale(1.3333334F, 1.3333334F, 1.3333334F);
        Material eyeMaterial = state.isFullPower ? ConduitRenderer.OPEN_EYE_TEXTURE : ConduitRenderer.CLOSED_EYE_TEXTURE;
        collector.submitModelPart(
                this.eye,
                poseStack,
                eyeMaterial.renderType(RenderTypes::entityCutoutNoCull),
                state.lightCoords,
                OverlayTexture.NO_OVERLAY,
                this.materials.get(eyeMaterial),
                tint,
                state.breakProgress
        );
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(CelestialConduitBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX(), pos.getY() - 0.25, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.25, pos.getZ() + 1.0);
    }
}
