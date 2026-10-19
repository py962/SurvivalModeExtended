package com.terrainlab.client;

import com.terrainlab.player.StatFlash;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * Client-side timers for the golden HUD flash on discrete stat gains (fed by StatFlashPacket).
 * Each bar pulses twice over {@link #DURATION_MS}: intensity is sin^2, so it rises and falls
 * to zero at the midpoint and again at the end. Wall-clock based so the pulse is smooth
 * between ticks.
 */
public final class ClientStatFlash {

    public static final long DURATION_MS = 1000L;
    private static final int PULSES = 2;
    /** Peak opacity of the gold overlay. */
    private static final float MAX_ALPHA = 0.85F;

    /** Gold tint applied to the white flash masks (RGB only; alpha comes from the pulse). */
    public static final int GOLD_RGB = 0xFFD94A;

    private static long healthStart = Long.MIN_VALUE;
    private static long hungerStart = Long.MIN_VALUE;
    private static long energyStart = Long.MIN_VALUE;

    private ClientStatFlash() {
    }

    public static void trigger(int mask) {
        long now = Util.getMillis();
        if ((mask & StatFlash.HEALTH) != 0) healthStart = now;
        if ((mask & StatFlash.HUNGER) != 0) hungerStart = now;
        if ((mask & StatFlash.ENERGY) != 0) energyStart = now;
    }

    public static float health() {
        return intensity(healthStart);
    }

    public static float hunger() {
        return intensity(hungerStart);
    }

    public static float energy() {
        return intensity(energyStart);
    }

    /** ARGB gold at the given 0..1 intensity, or 0 when not flashing. */
    public static int goldColor(float intensity) {
        int a = Mth.clamp(Math.round(intensity * MAX_ALPHA * 255.0F), 0, 255);
        return (a << 24) | GOLD_RGB;
    }

    private static float intensity(long start) {
        if (start == Long.MIN_VALUE) {
            return 0.0F;
        }
        long elapsed = Util.getMillis() - start;
        if (elapsed < 0 || elapsed >= DURATION_MS) {
            return 0.0F;
        }
        float t = elapsed / (float) DURATION_MS;
        float s = Mth.sin(t * PULSES * Mth.PI);
        return s * s;
    }

    /** Clears all flashes (e.g. on disconnect). */
    public static void reset() {
        healthStart = hungerStart = energyStart = Long.MIN_VALUE;
    }
}
