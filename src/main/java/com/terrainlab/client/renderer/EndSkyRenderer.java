package com.terrainlab.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import com.terrainlab.mixin.SkyRendererAccessor;
import org.joml.Matrix4f;

public class EndSkyRenderer implements CustomSkyboxRenderer {

    // Independent flash slots that can overlap.
    private static final int FLASH_COUNT = 4;
    private final FlashState[] flashes = new FlashState[FLASH_COUNT];

    private static final float[][] COLOURS = {
            {1.0f, 1.0f, 1.0f},       // white (vanilla)
            {0.8f, 0.3f, 1.0f},       // purple
            {0.3f, 1.0f, 0.9f},       // cyan
            {1.0f, 0.3f, 0.8f},       // magenta
            {0.5f, 1.0f, 0.5f},       // pale green
            {1.0f, 0.6f, 0.2f},       // amber
    };

    public EndSkyRenderer() {
        RandomSource rng = RandomSource.create();
        for (int i = 0; i < FLASH_COUNT; i++) {
            flashes[i] = new FlashState(rng, i * 50);
        }
    }

    @Override
    public boolean renderSky(LevelRenderState levelState, SkyRenderState skyState,
                             Matrix4f modelViewMatrix, Runnable setupFog) {
        if (skyState.skybox != net.minecraft.world.level.dimension.DimensionType.Skybox.END) {
            return false;
        }

        setupFog.run();

        SkyRenderer skyRenderer = ((SkyRendererAccessor) Minecraft.getInstance().levelRenderer).getSkyRenderer();
        if (skyRenderer == null) return false;

        skyRenderer.renderEndSky();

        long gameTime = Minecraft.getInstance().level.getGameTime();
        for (FlashState flash : flashes) {
            flash.tick(gameTime);
            float intensity = flash.getIntensity();
            if (intensity > 1.0E-5F) {
                PoseStack poseStack = new PoseStack();
                float[] colour = COLOURS[flash.colourIndex];
                renderColouredFlash(skyRenderer, poseStack, intensity,
                        flash.xAngle, flash.yAngle, colour);
            }
        }

        return true;
    }

    private void renderColouredFlash(SkyRenderer skyRenderer, PoseStack poseStack,
                                     float intensity, float xAngle, float yAngle,
                                     float[] colour) {
        // renderEndFlash takes no colour, so only the intensity is scaled.
        skyRenderer.renderEndFlash(poseStack, intensity * colour[0],
                xAngle, yAngle);
    }

    private static class FlashState {
        private static final int[] INTERVALS = {150, 200, 300, 400};
        private static final int[] MIN_DURATIONS = {40, 60, 80, 100};
        private static final int[] MAX_DURATIONS = {120, 180, 250, 350};

        private final int intervalTicks;
        private final int minDuration;
        private final int maxDuration;
        int colourIndex;
        float xAngle;
        float yAngle;
        private int offset;
        private int duration;
        private long flashSeed = -1;
        private float intensity;
        private float oldIntensity;

        FlashState(RandomSource rng, int phase) {
            int idx = rng.nextInt(INTERVALS.length);
            intervalTicks = INTERVALS[idx];
            minDuration = MIN_DURATIONS[idx];
            maxDuration = MAX_DURATIONS[idx];
            colourIndex = rng.nextInt(COLOURS.length);
            // Stagger start times.
            flashSeed = -phase;
        }

        void tick(long gameTime) {
            long cycle = gameTime / intervalTicks;
            if (cycle != flashSeed) {
                RandomSource rng = RandomSource.create(cycle * 31L + intervalTicks);
                offset = Mth.randomBetweenInclusive(rng, 0, intervalTicks / 3);
                duration = Mth.randomBetweenInclusive(rng, minDuration,
                        Math.min(maxDuration, intervalTicks - offset));
                xAngle = Mth.randomBetween(rng, -60.0f, 10.0f);
                yAngle = Mth.randomBetween(rng, -180.0f, 180.0f);
                colourIndex = rng.nextInt(COLOURS.length);
                flashSeed = cycle;
            }
            oldIntensity = intensity;
            long phase = gameTime % intervalTicks;
            intensity = (phase >= offset && phase <= offset + duration)
                    ? Mth.sin((float)(phase - offset) * (float)Math.PI / duration)
                    : 0.0f;
        }

        float getIntensity() {
            return intensity;
        }
    }
}
