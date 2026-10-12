package com.terrainlab.client.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.terrainlab.blockentity.CelestialForgeBlockEntity;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Celestial Forge ritual visuals: the gem floats and turns above the forge while swirling rays of
 * its colour climb into the sky; thrown components orbit the gem and spiral into it as they are
 * transformed. Rays use the ender dragon's death-ray render types, which add light; the Death
 * ritual's black rays use a normally blended copy instead, since adding black adds nothing.
 */
public class CelestialForgeRenderer implements BlockEntityRenderer<CelestialForgeBlockEntity, CelestialForgeRenderState> {

    private static final float GEM_HEIGHT = 1.6F;   // above the forge's base
    private static final int RAY_COUNT = 28;
    private static final float RAY_LENGTH = 160.0F; // blocks: high enough to reach the sky
    private static final int FULL_BRIGHT = 0xF000F0;

    // The dragon-ray pipeline with ordinary alpha blending, so dark rays shade what is behind them.
    private static final RenderPipeline DARK_RAYS_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("terrainlab/pipeline/dark_rays")
            .withVertexShader("core/rendertype_lightning")
            .withFragmentShader("core/rendertype_lightning")
            .withDepthWrite(false)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .build();
    private static final RenderType DARK_RAYS =
            RenderType.create("terrainlab_dark_rays", RenderSetup.builder(DARK_RAYS_PIPELINE).sortOnUpload().createRenderSetup());

    private final ItemModelResolver itemModelResolver;

    public CelestialForgeRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public CelestialForgeRenderState createRenderState() {
        return new CelestialForgeRenderState();
    }

    @Override
    public void extractRenderState(CelestialForgeBlockEntity forge, CelestialForgeRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(forge, state, partialTick, cameraPos, crumblingOverlay);
        CelestialForgeBlockEntity.Phase phase = forge.phase();
        state.active = phase != CelestialForgeBlockEntity.Phase.IDLE;
        state.transforming = phase == CelestialForgeBlockEntity.Phase.TRANSFORM;
        state.time = forge.phaseTicks() + partialTick;
        state.transformProgress = state.transforming
                ? Mth.clamp(state.time / CelestialForgeBlockEntity.TRANSFORM_TICKS, 0.0F, 1.0F) : 0.0F;
        state.colour = CelestialForgeBlockEntity.rayColour(forge.variant());

        int seed = (int) forge.getBlockPos().asLong();
        itemModelResolver.updateForTopItem(state.gem, forge.gem(), ItemDisplayContext.FIXED, forge.getLevel(), null, seed);
        state.components.clear();
        int index = 1;
        for (ItemStack stack : forge.absorbed()) {
            for (int i = 0; i < stack.getCount(); i++) {
                ItemStackRenderState component = new ItemStackRenderState();
                itemModelResolver.updateForTopItem(component, stack, ItemDisplayContext.FIXED, forge.getLevel(), null, seed + index++);
                state.components.add(component);
            }
        }
    }

    @Override
    public void submit(CelestialForgeRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        if (!state.active) {
            return;
        }
        float time = state.time;
        float bob = Mth.sin(time * 0.1F) * 0.08F;

        // Rays, drawn twice like the dragon's: once for colour, once for depth.
        poseStack.pushPose();
        poseStack.translate(0.5F, GEM_HEIGHT + bob, 0.5F);
        if (state.colour == 0x000000) {
            submitRays(poseStack, collector, DARK_RAYS, state);
        } else {
            submitRays(poseStack, collector, RenderTypes.dragonRays(), state);
        }
        submitRays(poseStack, collector, RenderTypes.dragonRaysDepth(), state);
        poseStack.popPose();

        // The gem, turning; it swells and flashes as the transformation completes.
        if (!state.gem.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0.5F, GEM_HEIGHT + bob, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * (state.transforming ? 12.0F : 3.0F)));
            float scale = 0.6F + state.transformProgress * 0.3F;
            poseStack.scale(scale, scale, scale);
            state.gem.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        // Components orbit the gem, then spiral inward and shrink into it.
        int count = state.components.size();
        for (int i = 0; i < count; i++) {
            float angle = time * (state.transforming ? 0.35F : 0.08F) + i * Mth.TWO_PI / Math.max(count, 1);
            float radius = 0.9F * (1.0F - state.transformProgress);
            float height = GEM_HEIGHT + bob + Mth.sin(time * 0.15F + i) * 0.15F;
            poseStack.pushPose();
            poseStack.translate(0.5F + Mth.cos(angle) * radius, height, 0.5F + Mth.sin(angle) * radius);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 8.0F));
            float scale = 0.45F * (1.0F - state.transformProgress * 0.9F);
            poseStack.scale(scale, scale, scale);
            state.components.get(i).submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /**
     * Thin light shafts fanning upward from the gem and turning around it, tinted with the gem's
     * colour and fading to nothing at their tips. Brighter and tighter while transforming.
     */
    private static void submitRays(PoseStack poseStack, SubmitNodeCollector collector, RenderType type,
                                   CelestialForgeRenderState state) {
        float time = state.time;
        float fadeIn = Mth.clamp(state.transforming ? 1.0F : time / 40.0F, 0.0F, 1.0F);
        int centreAlpha = (int) (fadeIn * (state.transforming ? 230 : 170));
        int centre = ARGB.color(centreAlpha, state.colour);
        int edge = ARGB.color(0, state.colour);

        collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
            RandomSource random = RandomSource.create(9127L);
            Quaternionf rotation = new Quaternionf();
            for (int i = 0; i < RAY_COUNT; i++) {
                // Each shaft leans out from vertical by a fixed amount and swirls around the axis.
                float lean = (0.04F + random.nextFloat() * 0.22F) * (1.0F - state.transformProgress * 0.7F);
                float spin = random.nextFloat() * Mth.TWO_PI + time * (0.02F + random.nextFloat() * 0.03F)
                        * (state.transforming ? 4.0F : 1.0F);
                float length = RAY_LENGTH * (0.6F + random.nextFloat() * 0.4F);
                float width = (0.6F + random.nextFloat() * 1.2F) * (state.transforming ? 1.5F : 1.0F);

                rotation.rotationY(spin).rotateX(lean);
                Matrix4f m = new Matrix4f(pose.pose()).rotate(rotation);
                float hx = 0.866F * width; // sqrt(3)/2: a triangular shaft
                consumer.addVertex(m, 0.0F, 0.0F, 0.0F).setColor(centre);
                consumer.addVertex(m, -hx, length, -0.5F * width).setColor(edge);
                consumer.addVertex(m, hx, length, -0.5F * width).setColor(edge);
                consumer.addVertex(m, 0.0F, 0.0F, 0.0F).setColor(centre);
                consumer.addVertex(m, hx, length, -0.5F * width).setColor(edge);
                consumer.addVertex(m, 0.0F, length, width).setColor(edge);
                consumer.addVertex(m, 0.0F, 0.0F, 0.0F).setColor(centre);
                consumer.addVertex(m, 0.0F, length, width).setColor(edge);
                consumer.addVertex(m, -hx, length, -0.5F * width).setColor(edge);
            }
        });
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(CelestialForgeBlockEntity blockEntity) {
        return AABB.INFINITE;
    }
}
