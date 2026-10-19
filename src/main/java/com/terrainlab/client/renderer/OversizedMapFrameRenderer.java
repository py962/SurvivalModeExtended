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

    /**
     * Render state carrying the floor/ceiling orientation.
     */
    public static class OversizedMapFrameRenderState extends ItemFrameRenderState {
        public Direction flatFacing = Direction.NORTH;
    }

    @Override
    public ItemFrameRenderState createRenderState() {
        return new OversizedMapFrameRenderState();
    }

    @Override
    public void extractRenderState(
            OversizedMapFrameEntity entity,
            ItemFrameRenderState state,
            float partialTicks
    ) {
        super.extractRenderState(entity, state, partialTicks);

        if (state instanceof OversizedMapFrameRenderState oversized) {
            oversized.flatFacing = entity.getFlatFacing();
        }
    }

    /**
     * Extra quarter turns (about the frame normal) so a floor/ceiling map
     * reads upright for the player who placed it. Vanilla's rotation-0
     * floor map has its top to the north and its ceiling map its top to
     * the south, i.e. upright for a player facing north; each +90 degree
     * local Z turn moves the top clockwise (floor: N->E, ceiling: S->E).
     */
    private static int flatQuarterTurns(Direction direction, Direction flatFacing) {
        int facing2D = flatFacing.get2DDataValue(); // S=0 W=1 N=2 E=3

        return switch (direction) {
            case UP -> (facing2D + 2) & 3;   // map top points away from placer
            case DOWN -> (2 - facing2D) & 3; // map top points back at placer
            default -> 0;
        };
    }

    @Override
    public void submit(
            ItemFrameRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        // Do not call super.submit(): it would render a second,
        // normal-sized frame and its contents. Submit the name tag
        // (renamed map) ourselves, as EntityRenderer#submit would,
        // raised so it clears the top edge of a wall frame.
        Direction direction = state.direction;

        poseStack.pushPose();

        if (direction.getAxis().isHorizontal()) {
            poseStack.translate(0.0F, (size - 1) * 0.5F, 0.0F);
        }

        submitNameDisplay(state, poseStack, submitNodeCollector, camera);
        poseStack.popPose();

        poseStack.pushPose();

        Vec3 renderOffset = getRenderOffset(state);

        poseStack.translate(
                -renderOffset.x(),
                -renderOffset.y(),
                -renderOffset.z()
        );

        /*
         * The render origin is the entity position, which is the centre of
         * its bounding box: the footprint centre (offset from the anchor
         * block for 2x2), 0.46875 in front of the block layer's centre.
         * Move to the block layer's centre plane, exactly as vanilla does.
         */
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

        // Local frame now: X/Y span the footprint plane, +Z points into the
        // supporting surface. Orient floor/ceiling frames for the placer.
        Direction flatFacing = state instanceof OversizedMapFrameRenderState oversized
                ? oversized.flatFacing
                : Direction.NORTH;
        int quarterTurns = flatQuarterTurns(direction, flatFacing);

        if (quarterTurns != 0) {
            poseStack.rotateDegrees(Axis.ZP, quarterTurns * 90.0F);
        }

        // Render the enlarged frame model exactly once: scaled across the
        // footprint in-plane only (depth unscaled, so its back stays flush
        // with the surface at local z = +0.5), then centred like vanilla.
        if (!state.frameModel.isEmpty()) {
            poseStack.pushPose();
            poseStack.scale(size, size, 1.0F);
            poseStack.translate(-0.5F, -0.5F, -0.5F);

            state.frameModel.submitWithZOffset(
                    poseStack,
                    submitNodeCollector,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor
            );

            poseStack.popPose();
        }

        // Position the displayed item or map in front of the frame back.
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
                // One filled map enlarged across the entire frame
                // (in-plane only, matching the frame model).
                poseStack.pushPose();

                poseStack.scale(size, size, 1.0F);

                // Map frames rotate in steps of 2 (90 degrees per click).
                poseStack.rotateDegrees(
                        Axis.ZP,
                        (state.rotation / 2) * 90.0F
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
                 * Display items without map data (e.g. a terrainlab:atlas
                 * with no map yet) are rendered through their item renderer
                 * and enlarged to fit the oversized frame.
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
