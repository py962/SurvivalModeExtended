package com.terrainlab.temperature;

/**
 * Player body-temperature tuning. BASE_DRIFT_RATE and DAMAGE_SCALE_FACTOR are
 * calibrated together so a player with early/mid-game countermeasures survives
 * roughly one in-game day at the coldest (-30C) and hottest (50C) biomes.
 */
public final class PlayerTemperatureConstants {

    public static final double COMFORTABLE_RESTING_VALUE = 20.0;
    public static final double COMFORTABLE_BAND_LOW = 15.0;
    public static final double COMFORTABLE_BAND_HIGH = 25.0;
    public static final double FREEZING_EFFECT_THRESHOLD = -0.0;
    public static final double HEATSTROKE_EFFECT_THRESHOLD = 40.0;
    public static final int EFFECT_REFRESH_TICKS = 5;
    public static final double BASE_DRIFT_RATE = 0.001; // ~2 min to approach ambient from 20C

    // Fur multiplies the rate of cooling (strongly slowed) and heating (sped up).
    public static final double FUR_COOLING_RATE_MODIFIER = 0.1;
    public static final double FUR_HEATING_RATE_MODIFIER = 1.6;

    // Instant food pushes, degrees C; only applied within FOOD_PUSH_MIN/MAX_TEMP.
    public static final double COOLING_FOOD_PUSH = -3.0; // melon/water
    public static final double WARMING_FOOD_PUSH = 5.0;  // stews/soup
    public static final double FOOD_PUSH_MIN_TEMP = 10.0;
    public static final double FOOD_PUSH_MAX_TEMP = 30.0;

    // Added to the local temperature target every tick the player is on fire, degrees C.
    public static final double ON_FIRE_HEAT_CONTRIBUTION = 20.0;

    // Heatstroke drains energy: HEATSTROKE_ENERGY_DRAIN (1% of the 100% scale) every interval.
    public static final float HEATSTROKE_ENERGY_DRAIN = 0.01F;
    public static final int HEATSTROKE_ENERGY_DRAIN_INTERVAL_TICKS = 100; // 5 s

    // Per-tick restore rate of effect duration toward 20.
    public static final double EFFECT_RESTORE_RATE = 0.15;

    // Health damage formula: damageRate(severity) = max(0, severity-20)^2 * SCALE_FACTOR
    public static final double DAMAGE_SEVERITY_THRESHOLD = 20.0;
    public static final double DAMAGE_SCALE_FACTOR = 1.5316; // calibrated with BASE_DRIFT_RATE
    public static final double DAMAGE_ACCUMULATOR_THRESHOLD_PER_HALF_HEART = 20000.0;

    private PlayerTemperatureConstants() {
    }
}
