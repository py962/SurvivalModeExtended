package com.terrainlab.season;

/**
 * A biome's eight base Celsius values (four TemperatureSeasons x day/night) and its precipitation
 * behaviour.
 */
public record SeasonalBiomeData(
        double midwinterDay,
        double midwinterNight,
        double nearWinterDay,
        double nearWinterNight,
        double nearSummerDay,
        double nearSummerNight,
        double midsummerDay,
        double midsummerNight,
        PrecipitationBehaviour precipitation
) {

    /** Raw (pre-blend, pre-altitude) Celsius value for the given temperature season and time of day. */
    public double getTemperature(TemperatureSeason season, boolean isDay) {
        return switch (season) {
            case MIDWINTER -> isDay ? midwinterDay : midwinterNight;
            case NEAR_WINTER -> isDay ? nearWinterDay : nearWinterNight;
            case NEAR_SUMMER -> isDay ? nearSummerDay : nearSummerNight;
            case MIDSUMMER -> isDay ? midsummerDay : midsummerNight;
        };
    }

    /** Day value blended toward the night value by nightFraction (0 = day, 1 = night). */
    public double getTemperature(TemperatureSeason season, double nightFraction) {
        double day = getTemperature(season, true);
        double night = getTemperature(season, false);
        return day + (night - day) * nightFraction;
    }

    /** Blends from the day value (fraction 0) toward the night value (fraction 1) within one season. */
    public double interpolate(TemperatureSeason season, double dayFraction, double nightFraction, boolean isDay) {
        double day = getTemperature(season, true);
        double night = getTemperature(season, false);
        double fraction = isDay ? dayFraction : nightFraction;
        return day + (night - day) * fraction;
    }

    /**
     * Per-biome precipitation across the wet season (autumn + winter) and dry season (spring + summer);
     * consumed by SeasonalPrecipitation on top of the global rain cycle.
     */
    public enum PrecipitationBehaviour {
        /** Never precipitates, in any season. */
        NONE,
        /** Precipitates only in the wet season (subject to the global rain flag); never in the dry season. */
        SEASONAL_WET_DRY,
        /** Precipitates in every season when the global rain flag is on; rain vs. snow by computed temperature. */
        YEAR_ROUND_RAIN,
        /** As YEAR_ROUND_RAIN. */
        FOUR_SEASONS_NO_SNOW_SETTLING,
        /** Every season; forced snow in MIDWINTER, otherwise rain vs. snow by computed temperature. */
        FOUR_SEASONS_SNOW_IN_WINTER,
        /** Always snow when the global rain flag is on. */
        SNOW_ALL_YEAR_NO_THAW
    }
}
