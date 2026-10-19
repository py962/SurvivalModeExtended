package com.terrainlab.village;

/**
 * Tuning for the profession work behaviours in {@code com.terrainlab.village.work} (farmer extra
 * harvest and deposits, butcher husbandry, fisherman fishing). All times are game ticks, all
 * distances blocks.
 */
public final class VillagerWorkConstants {

    private VillagerWorkConstants() {
    }

    // ---------------------------------------------------------------- shared

    /** Work area: horizontal radius around the villager's job site (JOB_SITE memory). */
    public static final int RANGE = 16;
    /** Work area: vertical half-height around the job site. */
    public static final int RANGE_VERTICAL = 6;
    /** Walk speed modifier, same as vanilla villager work packages. */
    public static final float WALK_SPEED = 0.5F;
    /** Longest a single walk leg (to a target, animal, chest or fishing spot) may take before giving up. */
    public static final int TRIP_TIMEOUT_TICKS = 400;
    /** Delay before a behaviour whose plan found nothing to do checks again. */
    public static final int IDLE_RETRY_TICKS = 200;
    /** Delay before retrying after a failed (timed-out / invalidated) trip. */
    public static final int FAILED_RETRY_TICKS = 600;
    /** Ticks the chest lid stays open before and after items move. */
    public static final int CHEST_OPEN_TICKS = 10;
    /** Reach for acting on a block or animal, measured from the villager's feet to the target centre. */
    public static final double ACT_REACH = 2.6D;

    // ---------------------------------------------------------------- deposits

    /** Minimum time between regular produce deliveries to the village chest. */
    public static final int DEPOSIT_INTERVAL_TICKS = 2400;
    /** "Inventory filling": a delivery is also due when at most this many slots are empty. */
    public static final int DEPOSIT_WHEN_FREE_SLOTS_AT_MOST = 1;
    /** ...provided at least this many deliverable items are carried. */
    public static final int FILLING_MIN_ITEMS = 8;

    // ---------------------------------------------------------------- farmer

    /** Potatoes and carrots each kept in the farmer's inventory for replanting. */
    public static final int SEED_CROP_RESERVE = 8;
    /** Wheat per loaf of bread. */
    public static final int WHEAT_PER_BREAD = 3;
    /** Beetroot per bowl of beetroot soup (no bowl needed). */
    public static final int BEETROOT_PER_SOUP = 6;
    /** Time between scans for mature pumpkins, melons and sugar cane. */
    public static final int FARMER_SCAN_INTERVAL_TICKS = 300;
    /** Most blocks harvested in one run before the farmer goes back to vanilla work. */
    public static final int FARMER_MAX_HARVESTS_PER_RUN = 8;

    // ---------------------------------------------------------------- butcher

    /** Breed a species when fewer adults than this are near the job site... */
    public static final int BREED_BELOW = 4;
    /** ...and at least this many are present. */
    public static final int BREED_MIN_ADULTS = 2;
    /** Slaughter one adult when more than this many are near the job site. */
    public static final int SLAUGHTER_ABOVE = 8;
    /** Per-species pause between two breedings by the same butcher. */
    public static final int BREED_COOLDOWN_TICKS = 3600;
    /** Pause between two slaughters by the same butcher. */
    public static final int SLAUGHTER_COOLDOWN_TICKS = 2400;
    /** The two parents must be at most this far apart (vanilla BreedGoal searches 8 blocks). */
    public static final double BREED_PARTNER_RADIUS = 8.0D;
    /** Time between animal scans. */
    public static final int BUTCHER_SCAN_INTERVAL_TICKS = 200;

    // ---------------------------------------------------------------- fisherman

    public static final int FISH_MIN_TICKS = 1800;
    public static final int FISH_MAX_TICKS = 4200;
    /** Mean of the uniform wait above (documentation only). */
    public static final int FISH_MEAN_TICKS = (FISH_MIN_TICKS + FISH_MAX_TICKS) / 2;
    /** Catch items carried before the fisherman delivers to the chest. */
    public static final int FISH_DELIVER_AT = 4;
    /** Catch items kept when the village has no chest; fishing stops at this many. */
    public static final int FISH_MAX_WITHOUT_CHEST = 16;
    /** Weights (percent) in temperate water: cod / salmon / dried kelp. */
    public static final int FISH_COD_WEIGHT = 60;
    public static final int FISH_SALMON_WEIGHT = 25;
    public static final int FISH_KELP_WEIGHT = 15;
    /** Weights in cold biomes (base temperature below {@link #FISH_COLD_TEMPERATURE}). */
    public static final int FISH_COLD_COD_WEIGHT = 35;
    public static final int FISH_COLD_SALMON_WEIGHT = 50;
    public static final float FISH_COLD_TEMPERATURE = 0.3F;
    /** Time between scans for open water after a scan found none. */
    public static final int FISH_WATER_SCAN_INTERVAL_TICKS = 1200;
}
