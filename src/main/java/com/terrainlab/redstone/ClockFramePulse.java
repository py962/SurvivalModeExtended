package com.terrainlab.redstone;

/**
 * Timing for the Clock item-frame redstone pulse: rotation 0-7 selects a period
 * of 20 ticks doubling per step (up to 2560), and each period starts with a
 * 2-tick pulse. Delivered to comparators by ItemFrameRedstoneHandler.
 */
public final class ClockFramePulse {

    /** Length of the pulse itself, in ticks. */
    public static final int PULSE_LENGTH_TICKS = 2;

    /** Period at rotation 0 (the fastest), in ticks. */
    public static final long BASE_PERIOD_TICKS = 20L;

    private ClockFramePulse() {
    }

    /**
     * @param rotation the frame's rotation, 0-7
     * @return the pulse period in ticks: 20, 40, 80, 160, 320, 640, 1280, or 2560
     */
    public static long periodForRotation(int rotation) {
        int clamped = ((rotation % 8) + 8) % 8;
        return BASE_PERIOD_TICKS << clamped;
    }

    /**
     * @param ticksIntoCycle ticks elapsed since the current period started
     * @param period         this cycle's period, from periodForRotation
     * @return true for the first PULSE_LENGTH_TICKS ticks of every period
     */
    public static boolean isPulseHigh(long ticksIntoCycle, long period) {
        long t = ticksIntoCycle % period;
        return t < PULSE_LENGTH_TICKS;
    }
}
