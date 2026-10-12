package com.terrainlab.village;

/**
 * Per-tier minimums for population, happiness (0..1), infrastructure and trade quality. A
 * village meets a tier only when every minimum is met at once.
 *
 * <p>Infrastructure is bell-only for now, so its minimum is always met. Tier 4's trade-quality
 * minimum is 45 of a possible 65 (13 vanilla professions x Master level 5), so a town no longer
 * needs every profession at Master; revisit it if custom professions are added.
 */
public record VillageTierRequirements(
        int minPopulation,
        double minHappiness,
        int minInfrastructure,
        int minTradeQuality
) {

    public static final VillageTierRequirements TIER_2_HAMLET = new VillageTierRequirements(
            5, 0.3, VillageConstants.INFRASTRUCTURE_BELL_POINTS, 1);

    public static final VillageTierRequirements TIER_3_VILLAGE = new VillageTierRequirements(
            15, 0.6, VillageConstants.INFRASTRUCTURE_BELL_POINTS, 13);

    public static final VillageTierRequirements TIER_4_TOWN = new VillageTierRequirements(
            25, 0.9, VillageConstants.INFRASTRUCTURE_BELL_POINTS, 45);

    /** Tier 1 (Outpost) is the baseline - no requirements to clear, returns null. */
    public static VillageTierRequirements forTier(VillageTier tier) {
        return switch (tier) {
            case TIER_1_OUTPOST -> null;
            case TIER_2_HAMLET -> TIER_2_HAMLET;
            case TIER_3_VILLAGE -> TIER_3_VILLAGE;
            case TIER_4_TOWN -> TIER_4_TOWN;
        };
    }

    /** Raw computed values for one village on one daily check. */
    public record Metrics(int population, double happiness, int infrastructure, int tradeQuality) {
    }

    /** True only if EVERY field is at or above this requirement's minimum. */
    public boolean isMetBy(Metrics m) {
        return m.population() >= minPopulation
                && m.happiness() >= minHappiness
                && m.infrastructure() >= minInfrastructure
                && m.tradeQuality() >= minTradeQuality;
    }

    /**
     * Promotion check: every field at or above this tier's minimum. Margins apply only when
     * demoting (see {@link #isFailedWithMarginBy(Metrics)}).
     */
    public boolean isClearedWithMarginBy(Metrics m) {
        return m.population() >= promotionPopulation()
                && m.happiness() >= promotionHappiness()
                && m.infrastructure() >= minInfrastructure
                && m.tradeQuality() >= promotionTradeQuality();
    }

    // Requirements for promotion into this tier; also shown on the bell info screen.
    public int promotionPopulation() {
        return minPopulation;
    }

    public double promotionHappiness() {
        return minHappiness;
    }

    public int promotionTradeQuality() {
        return minTradeQuality;
    }

    /**
     * Demotion check: true once any field falls more than its margin below this (current) tier's
     * minimum.
     */
    public boolean isFailedWithMarginBy(Metrics m) {
        boolean stillHoldsWithMargin =
                m.population() >= minPopulation - VillageConstants.TIER_MARGIN_POPULATION
                        && m.happiness() >= minHappiness - VillageConstants.TIER_MARGIN_HAPPINESS
                        && m.infrastructure() >= minInfrastructure - VillageConstants.TIER_MARGIN_INFRASTRUCTURE
                        && m.tradeQuality() >= minTradeQuality - VillageConstants.TIER_MARGIN_TRADE;
        return !stillHoldsWithMargin;
    }
}
