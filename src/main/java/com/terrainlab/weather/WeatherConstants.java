
package com.terrainlab.weather;

/**
 * Tuning constants for seasonal weather.
 *
 * Global percentages control the shared Overworld weather cycle.
 * Local percentages control which biome groups receive each global rain spell.
 *
 * All probability settings are percentages from 0.0 to 100.0.
 * Durations are in ticks.
 */
public final class WeatherConstants {

    private WeatherConstants() {
    }

    // =========================================================
    // GLOBAL WEATHER: AUTUMN + WINTER
    // =========================================================

    /** Chance per weather check to start rain when the global weather is clear. */
    public static final double GLOBAL_WET_SEASON_RAIN_START_PERCENT = 10.0;

    public static final int WET_SEASON_RAIN_DURATION_MIN_TICKS = 3_000;  // 2.5 min
    public static final int WET_SEASON_RAIN_DURATION_MAX_TICKS = 9_000; // 7.5 min


    // =========================================================
    // GLOBAL WEATHER: SPRING + SUMMER
    // =========================================================

    /** Chance per weather check to start a shower when the global weather is clear. */
    public static final double GLOBAL_DRY_SEASON_SHOWER_START_PERCENT = 5.0;

    public static final int DRY_SEASON_SHOWER_DURATION_MIN_TICKS = 1_200; // 1 min
    public static final int DRY_SEASON_SHOWER_DURATION_MAX_TICKS = 3_600; // 3 min


    // =========================================================
    // LOCAL PRECIPITATION: BIOME GROUPS
    // =========================================================

    /**
     * Savannas:
     * 100% eligible during autumn/winter; 0% during spring/summer.
     */
    public static final double LOCAL_SAVANNA_WET_SEASON_PERCENT = 100.0;
    public static final double LOCAL_SAVANNA_DRY_SEASON_PERCENT = 0.0;

    /**
     * Jungle group: jungle, bamboo jungle, sparse jungle and mangrove swamp.
     * Full wet season; ordinary forest-level precipitation in spring/summer.
     */
    public static final double LOCAL_JUNGLE_WET_SEASON_PERCENT = 100.0;
    public static final double LOCAL_JUNGLE_DRY_SEASON_PERCENT = 100.0;

    /**
     * Ordinary eligible biomes, including plains, forests and wooded badlands.
     * Reduce these values to make ordinary biomes drier.
     */
    public static final double LOCAL_DEFAULT_WET_SEASON_PERCENT = 20.0;
    public static final double LOCAL_DEFAULT_DRY_SEASON_PERCENT = 40.0;


    // =========================================================
    // GENERAL SETTINGS
    // =========================================================

    /** Poll interval for SeasonalWeatherCycleHandler; 1,200 ticks = 1 minute. */
    public static final long CHECK_INTERVAL_TICKS = 1_200L;

    /** Chance that a newly started autumn rain spell is a thunderstorm. */
    public static final double THUNDERSTORM_PERCENT = 10.0;

    /** Below this ambient Celsius temperature, precipitation is snow; otherwise it is rain. */
    public static final double SNOW_TEMPERATURE_THRESHOLD_C = 3.5;


    // =========================================================
    // SANDSTORMS
    // =========================================================

    /**
     * Sandstorm frequency (see Sandstorm#dustFactor): the dust wave runs from about -1.6 to 1.6 and
     * exceeds this threshold about 25% of the time; raise it for fewer sandstorms.
     */
    public static final double SANDSTORM_DUST_THRESHOLD = 0.6;
    /** Wave range over which a sandstorm fades in or out (about 1-2 minutes). */
    public static final double SANDSTORM_DUST_FADE = 0.15;

    public static final float SANDSTORM_NORMAL_STRENGTH = 0.6F;

    public static final float SANDSTORM_NORMAL_VISIBILITY = 64.0F;
    public static final float SANDSTORM_SEVERE_VISIBILITY = 16.0F;

    public static final float SANDSTORM_SHELTERED_FACTOR = 0.25F;

    public static final int SAND_LAYERS_NORMAL_MAX = 2;
    public static final int SAND_LAYERS_SEVERE_MAX = 4;

    public static final int SAND_LAYER_EROSION_CHANCE = 4;

    public static final int STORM_HUSK_INTERVAL_TICKS = 200;
    public static final int STORM_HUSK_MIN_DISTANCE = 24;
    public static final int STORM_HUSK_MAX_DISTANCE = 48;
    public static final int STORM_HUSK_CAP_NORMAL = 2;
    public static final int STORM_HUSK_CAP_SEVERE = 5;

    public static final int STORM_HUSK_FADE_AFTER_TICKS = 1_200;
    public static final double STORM_HUSK_FADE_CHANCE = 0.35;

    public static final int STORM_LAPIS_MIN = 3;
    public static final int STORM_LAPIS_MAX = 6;
    public static final int STORM_LAPIS_SEVERE_BONUS = 2;
    public static final int STORM_LAPIS_MIN_DISTANCE = 16;
    public static final int STORM_LAPIS_MAX_DISTANCE = 40;


    /**
     * Returns whether a percentage chance succeeds.
     * Values are clamped so 0 always fails and 100 always succeeds.
     */
    public static boolean rollPercent(
            java.util.random.RandomGenerator random,
            double percent
    ) {
        double clamped = Math.max(0.0, Math.min(100.0, percent));
        return random.nextDouble() * 100.0 < clamped;
    }
}
