package com.terrainlab.temperature;

/**
 * Thermometer display bands in degrees C, 10 degrees wide between the open-ended extremes.
 * TODO: calibrate the boundaries against SeasonalBiomeData extremes.
 */
public enum ThermometerBand {
    DEADLYCOLD(Double.NEGATIVE_INFINITY, -30.0),
    FREEZING(-30.0, -20.0),
    VERYCOLD(-20.0, -10.0),
    COLD(-10.0, 0.0),
    COOL(0.0, 10.0),
    MILD(10.0, 20.0),
    WARM(20.0, 30.0),
    HOT(30.0, 40.0),
    SCORCHING(40.0, 50.0),
    DEADLYHOT(50.0, Double.POSITIVE_INFINITY);

    public final double lowerBound;
    public final double upperBound;

    ThermometerBand(double lowerBound, double upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public static ThermometerBand fromCelsius(double celsius) {
        for (ThermometerBand band : values()) {
            if (celsius >= band.lowerBound && celsius < band.upperBound) {
                return band;
            }
        }
        return DEADLYHOT;
    }
}
