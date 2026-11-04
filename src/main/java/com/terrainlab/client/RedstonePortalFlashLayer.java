package com.terrainlab.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Redstone Portal arrival flash (fed by RedstonePortalFlashPacket): a redstone-red screen tint
 * with a darker red edge vignette and flickering horizontal static, snapping in and fading out
 * over {@link #DURATION_MS}.
 * <p>
 * The clock starts on the first frame drawn with no screen open, not when the packet arrives,
 * so a cross-dimension trip doesn't spend the flash behind the level-loading screen. A trigger
 * that never gets a free frame is dropped after {@link #PENDING_TIMEOUT_MS}.
 */
public class RedstonePortalFlashLayer implements GuiLayer {

    private static final long DURATION_MS = 900L;
    private static final long ATTACK_MS = 60L;
    private static final long PENDING_TIMEOUT_MS = 10_000L;

    private static final int TINT_RGB = 0xFF1A1A;
    private static final float TINT_MAX_ALPHA = 0.45F;

    private static final int EDGE_RGB = 0x8A0000;
    private static final float EDGE_MAX_ALPHA = 0.6F;
    private static final float EDGE_INNER_RADIUS = 0.5F;
    private static final float EDGE_OUTER_RADIUS = 1.1F;
    private static final int GRID_COLS = 40;
    private static final int GRID_ROWS = 24;

    private static final int STATIC_LINES = 14;
    private static final int STATIC_RGB = 0xFF6A5A;
    private static final float STATIC_MAX_ALPHA = 0.5F;

    private static final RandomSource STATIC_RANDOM = RandomSource.create();

    private static long pendingSince = Long.MIN_VALUE;
    private static long start = Long.MIN_VALUE;

    public static void trigger() {
        pendingSince = Util.getMillis();
        start = Long.MIN_VALUE;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long now = Util.getMillis();

        if (pendingSince != Long.MIN_VALUE) {
            if (now - pendingSince > PENDING_TIMEOUT_MS) {
                pendingSince = Long.MIN_VALUE;
            } else if (mc.screen == null) {
                start = now;
                pendingSince = Long.MIN_VALUE;
            }
        }
        if (start == Long.MIN_VALUE) return;

        long elapsed = now - start;
        if (elapsed >= DURATION_MS) {
            start = Long.MIN_VALUE;
            return;
        }
        float intensity = intensity(elapsed);
        if (intensity <= 0.002F) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        graphics.fill(0, 0, width, height, argb(TINT_RGB, TINT_MAX_ALPHA * intensity));
        drawEdge(graphics, width, height, intensity);
        drawStatic(graphics, width, height, intensity);
    }

    /** Fast linear attack, then a smooth ease-out to zero. */
    private static float intensity(long elapsed) {
        if (elapsed < ATTACK_MS) {
            return elapsed / (float) ATTACK_MS;
        }
        float t = (elapsed - ATTACK_MS) / (float) (DURATION_MS - ATTACK_MS);
        float inv = 1F - Mth.clamp(t, 0F, 1F);
        return inv * inv;
    }

    private static void drawEdge(GuiGraphics graphics, int width, int height, float intensity) {
        float centerX = width / 2F;
        float centerY = height / 2F;
        float cellW = (float) width / GRID_COLS;
        float cellH = (float) height / GRID_ROWS;
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                float dx = ((col + 0.5F) * cellW - centerX) / centerX;
                float dy = ((row + 0.5F) * cellH - centerY) / centerY;
                float dist = Mth.sqrt(dx * dx + dy * dy);
                float t = Mth.clamp((dist - EDGE_INNER_RADIUS) / (EDGE_OUTER_RADIUS - EDGE_INNER_RADIUS), 0F, 1F);
                float alpha = t * t * (3F - 2F * t) * EDGE_MAX_ALPHA * intensity;
                if (alpha <= 0.002F) continue;
                graphics.fill(Math.round(col * cellW), Math.round(row * cellH),
                        Math.round((col + 1) * cellW), Math.round((row + 1) * cellH),
                        argb(EDGE_RGB, alpha));
            }
        }
    }

    /** Thin bright lines at random heights, re-rolled every frame; fewer as the flash fades. */
    private static void drawStatic(GuiGraphics graphics, int width, int height, float intensity) {
        int lines = Math.round(STATIC_LINES * intensity);
        for (int i = 0; i < lines; i++) {
            int y = STATIC_RANDOM.nextInt(Math.max(1, height));
            int x1 = STATIC_RANDOM.nextInt(Math.max(1, width / 2));
            int x2 = x1 + width / 4 + STATIC_RANDOM.nextInt(Math.max(1, width / 2));
            float alpha = STATIC_MAX_ALPHA * intensity * (0.4F + 0.6F * STATIC_RANDOM.nextFloat());
            graphics.fill(x1, y, Math.min(width, x2), y + 1, argb(STATIC_RGB, alpha));
        }
    }

    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255F), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
