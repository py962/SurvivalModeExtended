package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.ClientAuroraState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.BindGroupLayouts;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws aurora ribbons after the sky pass, fading in and out with the server-synced
 * {@link ClientAuroraState}; the trigger logic lives server-side in ModAuroraHandler.
 * Client-only: registered on the game bus inside TerrainLab's client guard.
 * <p>
 * Ribbon geometry and colour technique adapted from Saereth's Auroral mod
 * (MIT licensed, github.com/breakinblocks/auroral).
 */
public class AuroraSkyRenderer {

    private static final int RIBBON_COUNT = 4;
    private static final int SEGMENTS_PER_RIBBON = 48;
    private static final float RIBBON_Y_OFFSET = 80.0f;
    private static final float RIBBON_Y_HEIGHT = 60.0f;
    private static final float RIBBON_RADIUS = 200.0f;

    private static final float WAVE_SPEED = 0.015f;
    private static final float SHIMMER_SPEED = 0.03f;
    private static final float DRIFT_SPEED = 0.002f;
    private static final float TIME_CYCLE = 100000.0f;

    private static final float INTENSITY_LERP_SPEED = 0.05f;
    private static float smoothedIntensity = 0.0f;

    // Blend toward a Celestial Forge ritual's colour (0..1), eased per frame.
    private static final float TINT_LERP_SPEED = 0.02f;
    private static float smoothedTint = 0.0f;

    private static final int[] tempColor = new int[4];

    private static long lastLogTick = -1;

    @Nullable
    private static GpuBuffer cachedVertexBuffer;

    @Nullable
    private static GpuBuffer cachedDarkVertexBuffer;

    // Set while building the dark pass: ribbons drawn as translucent black that blot out the sky.
    private static boolean buildingDarkPass = false;

    @Nullable
    private static RenderPipeline darkAuroraPipeline;

    /** Normal alpha blending, so black ribbons darken the sky (the additive pipeline cannot). */
    private static RenderPipeline getDarkAuroraPipeline() {
        if (darkAuroraPipeline == null) {
            darkAuroraPipeline = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withLocation("terrainlab/pipeline/aurora_dark")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    // No depth-stencil state: no depth test and no depth write.
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
                    .build();
        }
        return darkAuroraPipeline;
    }

    @Nullable
    private static RenderPipeline auroraPipeline;

    private static RenderPipeline getAuroraPipeline() {
        if (auroraPipeline == null) {
            auroraPipeline = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withLocation("terrainlab/pipeline/aurora")
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withColorTargetState(new ColorTargetState(new BlendFunction(
                            BlendFactor.SRC_ALPHA,
                            BlendFactor.ONE,
                            BlendFactor.ONE,
                            BlendFactor.ZERO
                    )))
                    .withCull(false)
                    // No depth-stencil state: no depth test and no depth write.
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
                    .build();
        }
        return auroraPipeline;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent.AfterSky event) {
        boolean serverActive = ClientAuroraState.isActive();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            long gameTime = mc.level.getGameTime();
            if (gameTime != lastLogTick && gameTime % 100 == 0) {
                lastLogTick = gameTime;
                // TerrainLab.LOGGER.info(
                //         "[AURORA_CLIENT] render check: serverActive={}, smoothedIntensity={}",
                //         serverActive, smoothedIntensity);
            }
        }

        if (!serverActive) {
            smoothedIntensity = Math.max(0.0f, smoothedIntensity - INTENSITY_LERP_SPEED);
            if (smoothedIntensity <= 0.01f) {
                return;
            }
        } else {
            smoothedIntensity = Math.min(1.0f, smoothedIntensity + INTENSITY_LERP_SPEED);
        }

        if (mc.level == null || mc.player == null) {
            return;
        }

        boolean tinted = ClientAuroraState.isTinted(mc.level.getGameTime());
        smoothedTint = tinted
                ? Math.min(1.0f, smoothedTint + TINT_LERP_SPEED)
                : Math.max(0.0f, smoothedTint - TINT_LERP_SPEED);

        // A black tint (the Death ritual) cannot be added to the sky, so it fades the coloured
        // ribbons out and draws dark ones over them instead.
        float darkness = isDarkTint(ClientAuroraState.tintColour()) ? smoothedTint : 0.0f;
        if (darkness < 1.0f) {
            renderAurora(event.getLevelRenderState(), smoothedIntensity * (1.0f - darkness), false);
        }
        if (darkness > 0.0f) {
            renderAurora(event.getLevelRenderState(), smoothedIntensity * darkness, true);
        }
    }

    private static boolean isDarkTint(int colour) {
        int r = (colour >> 16) & 0xFF, g = (colour >> 8) & 0xFF, b = colour & 0xFF;
        return Math.max(r, Math.max(g, b)) < 24;
    }

    private static void renderAurora(LevelRenderState levelRenderState, float intensity, boolean dark) {
        buildingDarkPass = dark;
        long gameTime = levelRenderState.gameTime;
        float time = (gameTime % (long) TIME_CYCLE) * 0.05f;

        int verticesPerRibbon = (SEGMENTS_PER_RIBBON + 1) * 2;
        int degenerateVertices = 2 * (RIBBON_COUNT - 1);
        int totalVertices = (verticesPerRibbon * RIBBON_COUNT) + degenerateVertices;
        int vertexSize = DefaultVertexFormat.POSITION_COLOR.getVertexSize();
        int bufferSize = totalVertices * vertexSize;

        MeshData meshData = null;
        try (ByteBufferBuilder byteBuffer = ByteBufferBuilder.exactlySized(bufferSize)) {
            BufferBuilder builder = new BufferBuilder(byteBuffer, PrimitiveTopology.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            for (int ribbon = 0; ribbon < RIBBON_COUNT; ribbon++) {
                if (ribbon > 0) {
                    addDegenerateConnection(builder, ribbon, time);
                }
                buildRibbonVertices(builder, ribbon, time, intensity);
            }

            meshData = builder.build();
            if (meshData == null) {
                TerrainLab.LOGGER.warn("[AURORA_CLIENT] renderAurora: builder.build() returned null, aborting frame");
                return;
            }

            GpuBuffer vertexBuffer = getOrCreateVertexBuffer(meshData, dark);
            if (vertexBuffer == null) {
                meshData.close();
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            var renderTarget = mc.gameRenderer.mainRenderTarget();
            if (renderTarget == null) {
                TerrainLab.LOGGER.warn("[AURORA_CLIENT] renderAurora: main render target is null, aborting frame");
                meshData.close();
                return;
            }

            var colorView = renderTarget.getColorTextureView();
            var depthView = renderTarget.getDepthTextureView();
            if (colorView == null) {
                TerrainLab.LOGGER.warn("[AURORA_CLIENT] renderAurora: color texture view is null, aborting frame");
                meshData.close();
                return;
            }

            GpuBufferSlice transformSlice = RenderSystem.getDynamicUniforms().writeTransform(
                    RenderSystem.getModelViewMatrixCopy(),
                    new Vector4f(1.0f, 1.0f, 1.0f, 1.0f),
                    new Vector3f(0, 0, 0),
                    new Matrix4f()
            );

            try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder()
                    .createRenderPass(
                            () -> "TerrainAurora",
                            colorView,
                            Optional.empty(),
                            depthView,
                            OptionalDouble.empty()
                    )) {
                renderPass.setPipeline(RenderSystem.getCompiledPipeline(dark ? getDarkAuroraPipeline() : getAuroraPipeline()));
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", transformSlice);
                renderPass.setVertexBuffer(0, vertexBuffer.slice());
                renderPass.draw(totalVertices, 1, 0, 0);
            }
        } finally {
            if (meshData != null) {
                meshData.close();
            }
        }
    }

    @Nullable
    private static GpuBuffer getOrCreateVertexBuffer(MeshData meshData, boolean dark) {
        try {
            GpuBuffer previous = dark ? cachedDarkVertexBuffer : cachedVertexBuffer;
            if (previous != null) {
                previous.close();
            }
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                    () -> dark ? "Terrain dark aurora vertex buffer" : "Terrain aurora vertex buffer",
                    GpuBuffer.USAGE_VERTEX,
                    meshData.vertexBuffer()
            );
            if (dark) {
                cachedDarkVertexBuffer = buffer;
            } else {
                cachedVertexBuffer = buffer;
            }
            return buffer;
        } catch (Exception e) {
            TerrainLab.LOGGER.warn("[AURORA-RENDER] Failed to create vertex buffer", e);
            return null;
        }
    }

    private static void addDegenerateConnection(BufferBuilder builder, int ribbonIndex, float time) {
        float ribbonOffset = ribbonIndex * (float) (Math.PI * 2.0 / RIBBON_COUNT);
        float drift = (time * DRIFT_SPEED) % (float) (Math.PI * 2.0) + ribbonIndex * 1.5f;
        float angle = ribbonOffset + drift;
        float x = (float) Math.cos(angle) * RIBBON_RADIUS;
        float z = (float) Math.sin(angle) * RIBBON_RADIUS;

        builder.addVertex(x, RIBBON_Y_OFFSET, z).setColor(0, 0, 0, 0);
        builder.addVertex(x, RIBBON_Y_OFFSET, z).setColor(0, 0, 0, 0);
    }

    private static void buildRibbonVertices(BufferBuilder builder, int ribbonIndex, float time, float intensity) {
        float ribbonOffset = ribbonIndex * (float) (Math.PI * 2.0 / RIBBON_COUNT);
        float colorPhase = ribbonIndex * 0.3f;
        float drift = (time * DRIFT_SPEED) % (float) (Math.PI * 2.0) + ribbonIndex * 1.5f;

        float waveTime1 = time * WAVE_SPEED;
        float waveTime2 = time * WAVE_SPEED * 1.7f;
        float waveTime3 = time * WAVE_SPEED * 0.5f;
        float shimmerTime = time * SHIMMER_SPEED;

        for (int i = 0; i <= SEGMENTS_PER_RIBBON; i++) {
            float segmentProgress = (float) i / SEGMENTS_PER_RIBBON;
            float angle = segmentProgress * (float) (Math.PI * 1.5) + ribbonOffset + drift;

            float x = (float) Math.cos(angle) * RIBBON_RADIUS;
            float z = (float) Math.sin(angle) * RIBBON_RADIUS;

            float wave1 = (float) Math.sin(waveTime1 + segmentProgress * 8.0f + ribbonIndex) * 15.0f;
            float wave2 = (float) Math.sin(waveTime2 + segmentProgress * 12.0f + ribbonIndex * 2.0f) * 8.0f;
            float wave3 = (float) Math.sin(waveTime3 + segmentProgress * 4.0f) * 20.0f;
            float yOffset = wave1 + wave2 + wave3 + RIBBON_Y_OFFSET;

            float heightFactor = (float) Math.sin(segmentProgress * Math.PI) * 0.8f + 0.2f;
            float height = RIBBON_Y_HEIGHT * heightFactor;

            float shimmer = (float) Math.sin(shimmerTime + segmentProgress * 10.0f + colorPhase) * 0.5f + 0.5f;
            float edgeFade = (float) Math.sin(segmentProgress * Math.PI);

            getAuroraColor(segmentProgress + colorPhase, shimmer, intensity * 0.3f, edgeFade);
            builder.addVertex(x, yOffset, z)
                    .setColor(tempColor[0], tempColor[1], tempColor[2], tempColor[3]);

            getAuroraColor(segmentProgress + colorPhase + 0.2f, shimmer, intensity * 0.7f, edgeFade);
            builder.addVertex(x, yOffset + height, z)
                    .setColor(tempColor[0], tempColor[1], tempColor[2], tempColor[3]);
        }
    }

    private static void getAuroraColor(float position, float shimmer, float intensity, float edgeFade) {
        position = position - (float) Math.floor(position);

        int r, g, b;
        if (position < 0.25f) {
            float t = position / 0.25f;
            r = (int) lerp(t, 50, 80);
            g = (int) lerp(t, 220, 230);
            b = (int) lerp(t, 100, 200);
        } else if (position < 0.5f) {
            float t = (position - 0.25f) / 0.25f;
            r = (int) lerp(t, 80, 200);
            g = (int) lerp(t, 230, 100);
            b = (int) lerp(t, 200, 220);
        } else if (position < 0.75f) {
            float t = (position - 0.5f) / 0.25f;
            r = (int) lerp(t, 200, 255);
            g = (int) lerp(t, 100, 150);
            b = (int) lerp(t, 220, 200);
        } else {
            float t = (position - 0.75f) / 0.25f;
            r = (int) lerp(t, 255, 50);
            g = (int) lerp(t, 150, 220);
            b = (int) lerp(t, 200, 100);
        }

        if (buildingDarkPass) {
            tempColor[0] = 0;
            tempColor[1] = 0;
            tempColor[2] = 0;
            tempColor[3] = clamp((int) (intensity * edgeFade * 255), 0, 255);
            return;
        }

        // Ritual tint: keep each point's brightness, take the hue from the forge's gem.
        if (smoothedTint > 0.0f && !isDarkTint(ClientAuroraState.tintColour())) {
            int tint = ClientAuroraState.tintColour();
            float brightness = Math.max(r, Math.max(g, b)) / 255.0f;
            r = (int) lerp(smoothedTint, r, ((tint >> 16) & 0xFF) * brightness);
            g = (int) lerp(smoothedTint, g, ((tint >> 8) & 0xFF) * brightness);
            b = (int) lerp(smoothedTint, b, (tint & 0xFF) * brightness);
        }

        float shimmerBoost = 0.8f + shimmer * 0.4f;
        r = clamp((int) (r * shimmerBoost), 0, 255);
        g = clamp((int) (g * shimmerBoost), 0, 255);
        b = clamp((int) (b * shimmerBoost), 0, 255);

        int alpha = clamp((int) (intensity * edgeFade * 180), 0, 255);

        tempColor[0] = r;
        tempColor[1] = g;
        tempColor[2] = b;
        tempColor[3] = alpha;
    }

    private static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static void dispose() {
        if (cachedVertexBuffer != null) {
            cachedVertexBuffer.close();
            cachedVertexBuffer = null;
        }
        if (cachedDarkVertexBuffer != null) {
            cachedDarkVertexBuffer.close();
            cachedDarkVertexBuffer = null;
        }
    }
}
