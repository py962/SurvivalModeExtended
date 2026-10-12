package com.terrainlab.village;

/**
 * Tunables for the villager and village systems. Values are placeholders pending playtesting.
 */
public final class VillageConstants {

    private VillageConstants() {
    }

    public static final long DAY_LENGTH_TICKS = 24_000L;

    // Each villager runs its staggered read once per this many ticks, offset by entity id.
    public static final long STAGGER_INTERVAL_TICKS = 100L;

    // Felt-temperature thresholds (deg C); same cut-offs as the player's Freezing / Heatstroke.
    public static final double THERMAL_COLD_THRESHOLD_C = 0.0;
    public static final double THERMAL_HOT_THRESHOLD_C = 40.0;
    // Time constant (ticks) for felt temperature to follow ambient: closes ~63% of the gap per 3 minutes.
    public static final double THERMAL_FELT_TIME_CONSTANT_TICKS = 3600.0;
    // A Shield conduit in range holds villagers at this felt temperature.
    public static final double THERMAL_SHIELD_COMFORT_C = 20.0;

    // Daytime happiness sampling interval (ticks); the daily tier check uses the day's average.
    public static final long HAPPINESS_SAMPLE_INTERVAL_TICKS = 1000L;

    // Fear lasts this long (ticks, 3 days) after the last trigger; each trigger restarts it.
    public static final long FEAR_DECAY_TICKS = 72_000L;

    // Radius (blocks) for spotting a hostile Monster in line of sight.
    public static final double FEAR_SIGHT_RADIUS = 8.0;

    // Tired once this many ticks have passed since the last completed sleep.
    public static final long TIRED_THRESHOLD_TICKS = 40_000L;

    // Dusk window length (ticks) after solar sunset.
    public static final long DUSK_LENGTH_TICKS = 1_500L;
    public static final long NOON_START_TICK = 5_500L;
    public static final long NOON_END_TICK = 6_500L;
    // Trapped: a day counts as trapped if horizontal wander from the day's start never reaches this (blocks).
    public static final double TRAPPED_WANDER_THRESHOLD_BLOCKS = 20.0;

    // Consecutive trapped days required before the villager is flagged; one free day resets the streak.
    public static final int TRAPPED_CONFIRM_STREAK_DAYS = 2;
    // Minimum membership radius (blocks) around a bell. The effective radius widens up to half the
    // distance to the nearest other bell; see VillageMembershipService#effectiveRadius.
    public static final double MIN_VILLAGE_MEMBERSHIP_RADIUS = 128.0;
    // Upper bound for isolated villages, which would otherwise reach half-way to a distant bell.
    public static final double MAX_VILLAGE_MEMBERSHIP_RADIUS = 192.0;

    // Poll interval (ticks) for the village economy/night-watch checks.
    public static final long DAILY_CHECK_POLL_INTERVAL_TICKS = 200L;

    public static final int FOOD_PER_VILLAGER_PER_DAY = 4;

    public static final int INFRASTRUCTURE_BELL_POINTS = 5;


    // Tier requirement margins (AND-gated, see VillageTierRequirements). Applied on the way down only:
    // a village drops a tier when any current-tier requirement is missed by more than its margin.
    public static final int TIER_MARGIN_POPULATION = 1;
    public static final double TIER_MARGIN_HAPPINESS = 0.05;
    // Infrastructure is currently bell-only, so no margin applies yet.
    public static final int TIER_MARGIN_INFRASTRUCTURE = 0;
    public static final int TIER_MARGIN_TRADE = 2;

    // Unused; superseded by TIER_UP_XP_LEVEL2/3/4.
    public static final int TIER_UP_XP = 50;
    public static final int TIER_DOWN_XP = 50;

    // Village XP and reputation schedule. XP is banked per village and released at the bell
    // (VillageBellRewardService); reputation is applied immediately (VillageReputationService).
    // Tier-up XP is paid only on a tier's first-ever upgrade.
    public static final int TIER_UP_XP_LEVEL2 = 100;
    public static final int TIER_UP_XP_LEVEL3 = 500;
    public static final int TIER_UP_XP_LEVEL4 = 2000;

    // Reputation given to players near the bell on a tier's first-ever upgrade. Hamlet's fades
    // (minor gossip); Village's and Town's are permanent (major gossip, shared 100 cap with curing).
    public static final int TIER_UP_REPUTATION_LEVEL2 = 10;
    public static final int TIER_UP_REPUTATION_LEVEL3 = 25;
    public static final int TIER_UP_REPUTATION_LEVEL4 = 50;

    // One-time, server-wide bonus for the first tier-3 village of each biome type.
    public static final int BIOME_MILESTONE_XP = 500;

    // Nightly sunset bonus: NIGHT_LEVEL2_XP for tier 3, NIGHT_LEVEL3_XP for tier 4.
    public static final int NIGHT_LEVEL2_XP = 20;
    public static final int NIGHT_LEVEL3_XP = 100;

    // Applied to the player who breaks a bell.
    public static final int DESTROY_BELL_REPUTATION = -150;

    // Radius (blocks) at which villagers notice a Sentry-trimmed player in line of sight.
    public static final double SENTRY_TRIM_SIGHT_RADIUS = 16.0;

    // Applied as MINOR_NEGATIVE gossip at the sighting villager's village.
    public static final int SENTRY_TRIM_REPUTATION_PENALTY = -10;

    // Per-player cooldown (ticks, 5 minutes) between Sentry-trim penalties.
    public static final long SENTRY_TRIM_REPUTATION_COOLDOWN_TICKS = 6_000L;
}
