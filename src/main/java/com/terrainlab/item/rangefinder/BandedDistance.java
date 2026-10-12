package com.terrainlab.item.rangefinder;

/** Maps a distance in blocks to one of {@link #BAND_COUNT} bands on a 1-2-5 scale (10 to 10000). */
public final class BandedDistance {

    private static final double[] THRESHOLDS = {10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
    public static final int BAND_COUNT = THRESHOLDS.length + 1;

    private BandedDistance() {
    }

    public static int getBand(double distance) {
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (distance < THRESHOLDS[i]) return i;
        }
        return THRESHOLDS.length;
    }

    public static float getBandFraction(double distance) {
        return getBand(distance) / (float) BAND_COUNT;
    }
}
