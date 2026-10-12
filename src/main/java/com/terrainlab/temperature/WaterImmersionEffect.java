package com.terrainlab.temperature;

/**
 * Water immersion cooling: wetness (0..1) is set to 1 while immersed and decays
 * linearly to 0 over DECAY_TICKS afterwards.
 */
public final class WaterImmersionEffect {

    public static final double COOLING_STRENGTH = 20.0; // degrees C at full wetness
    public static final long DECAY_TICKS = 600;

    private WaterImmersionEffect() {
    }

    /** Linear decay: subtracts elapsedTicks / DECAY_TICKS, floored at 0. Called once per tick. */
    public static double decay(double previousWetness, long elapsedTicks) {
        if (elapsedTicks <= 0) return previousWetness;
        double step = (elapsedTicks / (double) DECAY_TICKS);
        return Math.max(0.0, previousWetness - step);
    }

    public static double coolingContribution(double wetness) {
        return -wetness * COOLING_STRENGTH;
    }
}
