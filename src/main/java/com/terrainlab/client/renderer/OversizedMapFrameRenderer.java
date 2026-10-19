package com.terrainlab.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terrainlab.entity.OversizedMapFrameEntity;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

public class OversizedMapFrameRenderer
        extends ItemFrameRenderer<OversizedMapFrameEntity> {

    private final int size;
    private final MapRenderer mapRenderer;

    public OversizedMapFrameRenderer(
            EntityRendererProvider.Context context,
            int size
    ) {
        super(context);

        if (size != 2 && size != 3) {
            throw new IllegalArgumentException(
                    "Oversized map frame size must be 2 or 3"
            );
        }

        this.size = size;
        this.mapRenderer = context.getMapRenderer();
    }

    @Override
    public void submit(
            ItemFrameRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        // Do not call super.submit(): it would render a second,
        // normal-sized frame and its contents.

        poseStack.pushPose();

        Direction direction = state.direction;
        Vec3 renderOffset = getRenderOffset(state);

        poseStack.translate(
                -renderOffset.x(),
                -renderOffset.y(),
                -renderOffset.z()
        );

        /*
         * The 2x2 entity is anchored at its bottom-left wall block.
         * Shift its visual centre half a block up and along the horizontal
         * footprint axis. The 3x3 entity is already centred on its anchor.
         *
         * This translation is applied before rotation, so it follows
         * world coordinates.
         */
        if (size == 2) {
            Direction horizontal = direction.getCounterClockWise();

            poseStack.translate(
                    horizontal.getStepX() * 0.5F,
                    0.5F,
                    horizontal.getStepZ() * 0.5F
            );
        }

        // Move the frame model just in front of the supporting wall.
        poseStack.translate(
                direction.getStepX() * 0.46875F,
                direction.getStepY() * 0.46875F,
                direction.getStepZ() * 0.46875F
        );

        float xRot;
        float yRot;

        if (direction.getAxis().isHorizontal()) {
            xRot = 0.0F;
            yRot = 180.0F - direction.toYRot();
        } else {
            xRot = (float) (
                    -90 * direction.getAxisDirection().getStep()
            );
            yRot = 180.0F;
        }

        poseStack.rotateDegrees(Axis.XP, xRot);
        poseStack.rotateDegrees(Axis.YP, yRot);

        // Render the enlarged frame model exactly once.
        if (!state.frameModel.isEmpty()) {
            poseStack.pushPose();
            poseStack.scale(size, size, size);

            state.frameModel.submitWithZOffset(
                    poseStack,
                    submitNodeCollector,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor
            );

            poseStack.popPose();
        }

        // Position the displayed item or map in front of the frame.
        poseStack.translate(
                0.0F,
                0.0F,
                state.isInvisible ? 0.5F : 0.4375F
        );

        RenderItemInFrameEvent event = new RenderItemInFrameEvent(
                state,
                this,
                poseStack,
                submitNodeCollector
        );

        if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
            if (state.mapId != null) {
                // One filled map enlarged across the entire frame.
                poseStack.pushPose();

                poseStack.scale(size, size, size);

                int rotation = (state.rotation % 4) * 2;

                poseStack.rotateDegrees(
                        Axis.ZP,
                        rotation * 360.0F / 8.0F
                );

                poseStack.rotateDegrees(Axis.ZP, 180.0F);

                poseStack.scale(
                        0.0078125F,
                        0.0078125F,
                        0.0078125F
                );

                poseStack.translate(-64.0F, -64.0F, 0.0F);
                poseStack.translate(0.0F, 0.0F, -1.0F);

                int lightCoords = state.isGlowFrame
                        ? 15728850
                        : state.lightCoords;

                mapRenderer.render(
                        state.mapRenderState,
                        poseStack,
                        submitNodeCollector,
                        true,
                        lightCoords
                );

                poseStack.popPose();
            } else if (!state.item.isEmpty()) {
                /*
                 * Non-vanilla-map display items, including terrainlab:atlas,
                 * are rendered through their item renderer and enlarged to
                 * fit the oversized frame.
                 */
                poseStack.pushPose();

                poseStack.scale(size, size, size);

                poseStack.rotateDegrees(
                        Axis.ZP,
                        state.rotation * 360.0F / 8.0F
                );

                poseStack.scale(0.5F, 0.5F, 0.5F);

                int lightCoords = state.isGlowFrame
                        ? 15728880
                        : state.lightCoords;

                state.item.submit(
                        poseStack,
                        submitNodeCollector,
                        lightCoords,
                        OverlayTexture.NO_OVERLAY,
                        state.outlineColor
                );

                poseStack.popPose();
            }
        }

        poseStack.popPose();
    }
}