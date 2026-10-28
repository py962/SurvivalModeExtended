package com.terrainlab.energy;

/** Tuning constants for player energy, hunger drain and resting. Energy values are 0..MAX_ENERGY fractions. */
public final class PlayerEnergyConstants {

    public static final float SPAWN_ENERGY = 1.00F;
    public static final float REST_CAP = 1.00F;
    public static final float MAX_ENERGY = 1.20F;
    public static final float MIN_ENERGY = 0.0F;

    // Time for each activity to fully deplete energy from 100%: sprinting ~3 min 20 s,
    // jumping (~1 per 0.6s) ~8 min, held mining ~10 min, fighting (~1.6 clicks/s) ~10 min.
    public static final float SPRINT_DRAIN_PER_TICK = 0.00025F;
    public static final float JUMP_DRAIN = 0.0012F;
    public static final float MINING_SWING_DRAIN = 0.0011F;
    public static final float ATTACK_SWING_DRAIN = 0.0011F;
    public static final int MINING_DRAIN_INTERVAL_TICKS = 13;

    public static final double BOOST_JUMP_HEIGHT_BLOCKS = 1.5;
    public static final double BOOST_SPRINT_SPEED_MODIFIER = 0.25;

    public static final double ZERO_ENERGY_SHIELD_RAISE_MULTIPLIER = 1.5;
    // Extra drain per tick while a heavy wood shield is raised (by ShieldStats lightness); lightness 2+
    // has none.
    public static final float SHIELD_RAISED_DRAIN_PER_TICK_LIGHTNESS_0 = SPRINT_DRAIN_PER_TICK;
    public static final float SHIELD_RAISED_DRAIN_PER_TICK_LIGHTNESS_1 = 0.000125F;

    // Resting converts hunger into health first (until full), then energy (until REST_CAP).
    // TODO: TICKS_PER_HUNGER_POINT is no longer read by PlayerRestingSystem; remove if unused.
    public static final int TICKS_PER_HUNGER_POINT = 300; // 15 seconds
    public static final float HEALTH_PER_HUNGER_POINT = 1.0F; // half-heart
    public static final float ENERGY_PER_HUNGER_POINT = 0.25F; // 25%
    public static final int MIN_HUNGER_FOR_HEALTH_REST = 4;
    public static final int MIN_HUNGER_FOR_ENERGY_REST = 8;

    // Daytime covering a full night's sleep (8pm-4am bed window);
    // sleeping less scales the reward down proportionally.
    public static final int FULL_NIGHT_WINDOW_TICKS = 8000;
    // Hunger-point conversions granted for a full night (fraction 1.0).
    public static final int MAX_HUNGER_CONVERSIONS_PER_NIGHT = 4;

    // Hunger drains on a flat timer, independent of saturation/exhaustion.
    public static final int TICKS_PER_HUNGER_DRAIN = 18000; // 15 minutes
    public static final float SATURATION_BUFFER_LEVEL = 20.0F; // pinned so vanilla exhaustion never reaches hunger
    // Instant energy granted by Energy-tier food (Cocoa Beans).
    public static final float ENERGY_FOOD_PUSH = 0.10F;
    // Max Energy-tier food uses per in-game day (getDayTime() / 24000).
    public static final int ENERGY_FOOD_DAILY_CAP = 5;
    private PlayerEnergyConstants() {
    }
}
