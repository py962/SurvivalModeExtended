package com.terrainlab.temperature;

/**
 * Bubble Gauge dial: maps raw Y from -64 to 256 onto a 0..1 fraction (16-block minor marks, 64-block majors).
 */
public final class BubbleGaugeDial {

    public static final double MIN_Y = -64.0;
    public static final double MAX_Y = 256.0;
    public static final int FRAME_COUNT = 21; // one per 16 blocks, -64..256

    private BubbleGaugeDial() {
    }

    public static float getFraction(double y) {
        double clamped = Math.max(MIN_Y, Math.min(MAX_Y, y));
        return (float) ((clamped - MIN_Y) / (MAX_Y - MIN_Y));
    }
}
