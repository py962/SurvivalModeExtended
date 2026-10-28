
        package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModBiomeSets;
import com.terrainlab.season.SolarTime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class MarshMistClient {

    // Pale grey-green, scaled by vanilla's light level.
    private static final float[] MIST_COLOUR = {
            0.76F, 0.80F, 0.78F
    };

    // Fog distances at full mist.
    private static final float MIST_START = 40.0F;
    private static final float MIST_END = 50.0F;

    // Mist is full when looking horizontally or down, and fades to zero
    // when looking 40 degrees up.
    private static final float FULL_MIST_PITCH = 40.0F;

    // Eye height above terrain over which the mist clears.
    private static final float CLEAR_FROM_HEIGHT = 2.5F;
    private static final float CLEAR_AT_HEIGHT = 10.0F;

    // Solar-time windows:
    // {fade-in start, full strength, fade-out start, fade-out end}.
    private static final long[] MORNING = {
            22800L, 23600L, 800L, 2800L
    };

    private static final long[] EVENING = {
            10200L, 11600L, 13000L, 14200L
    };

    private static final int SAMPLE_INTERVAL = 10;
    private static final int SAMPLE_RADIUS = 2;
    private static final int SAMPLE_SPACING = 8;

    private static float biomeTarget = 0.0F;
    private static float biomeFraction = 0.0F;
    private static float exposure = 1.0F;
    private static float timeFactor = 0.0F;
    private static float heightFactor = 1.0F;
    private static float gazeFactor = 1.0F;

    private static long lastGazeNanos;

    private MarshMistClient() {
    }

    /**
     * Mist strength at the camera, before gaze direction is applied.
     */
    public static float strength() {
        return biomeFraction
                * timeFactor
                * Mth.lerp(exposure, 0.25F, 1.0F)
                * heightFactor;
    }

    /**
     * Mist stays at full strength looking horizontally or down; looking
     * up thins it (pitch is negative when looking up).
     * The transition is eased to avoid flickering.
     */
    private static float gaze(float pitchDegrees) {
        float target = 1.0F - smoothstep(
                Math.max(0.0F, -pitchDegrees) / FULL_MIST_PITCH
        );

        long now = System.nanoTime();

        float dt = lastGazeNanos == 0L
                ? 1.0F
                : Math.min(
                (now - lastGazeNanos) / 1.0E9F,
                1.0F
        );

        lastGazeNanos = now;

        gazeFactor += (target - gazeFactor)
                * Math.min(1.0F, dt * 8.0F);

        return gazeFactor;
    }

    private static float smoothstep(float x) {
        float t = Mth.clamp(x, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;

        if (level == null || player == null) {
            biomeTarget = 0.0F;
            biomeFraction = 0.0F;
            exposure = 1.0F;
            timeFactor = 0.0F;
            heightFactor = 1.0F;
            gazeFactor = 1.0F;
            lastGazeNanos = 0L;
            return;
        }

        if (minecraft.isPaused()) {
            return;
        }

        boolean overworld = level.dimension() == Level.OVERWORLD;

        if (!overworld) {
            biomeTarget = 0.0F;
        } else if (level.getGameTime() % SAMPLE_INTERVAL == 0) {
            biomeTarget = sampleMarsh(level, player.blockPosition());
        }

        BlockPos eye = BlockPos.containing(player.getEyePosition());

        float exposureTarget;

        if (level.canSeeSky(eye)) {
            exposureTarget = 1.0F;
        } else {
            float sky = level.getBrightness(LightLayer.SKY, eye) / 15.0F;
            exposureTarget = 0.6F * sky * sky;
        }

        // Determine eye height above terrain, ignoring leaves.
        int floor = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                eye.getX(),
                eye.getZ()
        );

        float above = (float) player.getEyeY() - floor;

        float heightTarget = 1.0F - smoothstep(
                (above - CLEAR_FROM_HEIGHT)
                        / (CLEAR_AT_HEIGHT - CLEAR_FROM_HEIGHT)
        );

        // Smooth biome, height and exposure changes.
        biomeFraction += (biomeTarget - biomeFraction) * 0.05F;
        heightFactor += (heightTarget - heightFactor) * 0.1F;
        exposure += (exposureTarget - exposure) * 0.1F;

        // Ease timeFactor toward the current solar-time factor.
        // This is an additive approach (not a multiply), so it can
        // rise from its initial value of zero.
        float timeTarget = overworld
                ? timeOfDayFactor(SolarTime.solarTimeOfDay(level))
                : 0.0F;

        timeFactor += (timeTarget - timeFactor) * 0.05F;
    }

    private static float sampleMarsh(ClientLevel level, BlockPos centre) {
        int marsh = 0;
        int total = 0;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx++) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz++) {
                pos.set(
                        centre.getX() + dx * SAMPLE_SPACING,
                        centre.getY(),
                        centre.getZ() + dz * SAMPLE_SPACING
                );

                total++;

                var biome = level.getBiome(pos);

                if (biome.is(ModBiomeSets.MARSH)
                        || biome.is(ModBiomeSets.FROZEN_MARSH)
                        || biome.is(net.minecraft.world.level.biome.Biomes.SWAMP)) {
                    marsh++;
                }
            }
        }

        return marsh / (float) total;
    }

    /**
     * Returns the stronger of the morning and evening mist curves.
     */
    static float timeOfDayFactor(long solarTime) {
        return Math.max(
                window(solarTime, MORNING),
                window(solarTime, EVENING)
        );
    }

    /**
     * Smooth trapezoid over a window that may wrap past 24000.
     */
    private static float window(long t, long[] w) {
        long base = w[0];
        long rise = Math.floorMod(w[1] - base, 24000L);
        long fullEnd = Math.floorMod(w[2] - base, 24000L);
        long fall = Math.floorMod(w[3] - base, 24000L);
        long u = Math.floorMod(t - base, 24000L);

        float linear;

        if (u < rise) {
            linear = u / (float) rise;
        } else if (u < fullEnd) {
            linear = 1.0F;
        } else if (u < fall) {
            linear = 1.0F - (u - fullEnd) / (float) (fall - fullEnd);
        } else {
            return 0.0F;
        }

        return linear * linear * (3.0F - 2.0F * linear);
    }

    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.ATMOSPHERIC) {
            return;
        }

        float k = strength() * gaze(event.getCamera().xRot());

        if (k <= 0.001F) {
            return;
        }

        var fog = event.getFogData();

        // Never loosen fog already tightened by another effect.
        fog.environmentalEnd = Math.min(
                fog.environmentalEnd,
                Mth.lerp(k, fog.environmentalEnd, MIST_END)
        );

        fog.environmentalStart = Math.min(
                fog.environmentalStart,
                Mth.lerp(k, fog.environmentalStart, MIST_START)
        );

        // Clouds soften slightly while remaining visible.
        fog.cloudEnd = Math.min(
                fog.cloudEnd,
                Mth.lerp(
                        k * 0.5F,
                        fog.cloudEnd,
                        MIST_END * 2.0F
                )
        );
    }

    @SubscribeEvent
    static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }

        float k = strength() * gazeFactor * 0.6F;

        if (k <= 0.001F) {
            return;
        }

        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();

        // Preserve vanilla daylight, night-time and storm darkening.
        float light = Mth.clamp(
                (0.299F * r + 0.587F * g + 0.114F * b) / 0.55F,
                0.08F,
                1.1F
        );

        event.setRed(Mth.clamp(
                Mth.lerp(k, r, MIST_COLOUR[0] * light),
                0.0F,
                1.0F
        ));

        event.setGreen(Mth.clamp(
                Mth.lerp(k, g, MIST_COLOUR[1] * light),
                0.0F,
                1.0F
        ));

        event.setBlue(Mth.clamp(
                Mth.lerp(k, b, MIST_COLOUR[2] * light),
                0.0F,
                1.0F
        ));
    }
}
