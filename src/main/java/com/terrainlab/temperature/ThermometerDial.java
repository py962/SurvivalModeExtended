package com.terrainlab.temperature;

/**
 * Maps an ambient Celsius temperature onto the 0..1 fraction used by the thermometer's
 * range_dispatch item model. The -40/60 range leaves display room for the deadly bands beyond
 * -30 and 50.
 */
public final class ThermometerDial {

    public static final double FLOOR_C = -40.0;
    public static final double CEILING_C = 60.0;

    private ThermometerDial() {
    }

    public static float getFraction(double celsius) {
        double clamped = Math.max(FLOOR_C, Math.min(CEILING_C, celsius));
        return (float) ((clamped - FLOOR_C) / (CEILING_C - FLOOR_C));
    }
}
