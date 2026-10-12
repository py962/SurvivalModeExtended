package com.terrainlab.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.Set;

/**
 * Mutable per-village state stored in {@link VillageSavedData} and keyed by {@link VillageId}:
 * hunger and tier results from the daily check, economy rewards and daytime happiness samples.
 */
public final class VillageRecord {

    private long lastCheckedDay = -1L;

    // Hunger
    private int foodStock = 0;
    private int requiredFoodStock = 0;
    private VillageHungerState hungerState = VillageHungerState.SUFFICIENT;
    private int populationLastCheck = 0;
    private int farmerCountLastCheck = 0;

    // Tier
    private VillageTier tier = VillageTier.TIER_1_OUTPOST;
    private double lastCompositeScore = 0.0;

    // Economy
    private final Set<VillageTier> xpGrantedTiers = new HashSet<>();
    private int pendingBellXp = 0;

    // Daytime happiness samples since the last daily check.
    private double happinessSum = 0.0;
    private int happinessSamples = 0;

    public long getLastCheckedDay() {
        return lastCheckedDay;
    }

    public void setLastCheckedDay(long lastCheckedDay) {
        this.lastCheckedDay = lastCheckedDay;
    }

    public int getFoodStock() {
        return foodStock;
    }

    public void setFoodStock(int foodStock) {
        this.foodStock = foodStock;
    }

    public int getRequiredFoodStock() {
        return requiredFoodStock;
    }

    public void setRequiredFoodStock(int requiredFoodStock) {
        this.requiredFoodStock = requiredFoodStock;
    }

    public VillageHungerState getHungerState() {
        return hungerState;
    }

    public void setHungerState(VillageHungerState hungerState) {
        this.hungerState = hungerState;
    }

    public int getPopulationLastCheck() {
        return populationLastCheck;
    }

    public void setPopulationLastCheck(int populationLastCheck) {
        this.populationLastCheck = populationLastCheck;
    }

    public int getFarmerCountLastCheck() {
        return farmerCountLastCheck;
    }

    public void setFarmerCountLastCheck(int farmerCountLastCheck) {
        this.farmerCountLastCheck = farmerCountLastCheck;
    }

    public VillageTier getTier() {
        return tier;
    }

    public void setTier(VillageTier tier) {
        this.tier = tier;
    }

    public double getLastCompositeScore() {
        return lastCompositeScore;
    }

    public void setLastCompositeScore(double lastCompositeScore) {
        this.lastCompositeScore = lastCompositeScore;
    }

    /** Tiers that have ever paid their first-upgrade reward; never cleared by a downgrade. */
    public Set<VillageTier> getXpGrantedTiers() {
        return xpGrantedTiers;
    }

    public int getPendingBellXp() {
        return pendingBellXp;
    }

    public void setPendingBellXp(int pendingBellXp) {
        this.pendingBellXp = pendingBellXp;
    }

    public void addHappinessSample(double happiness) {
        this.happinessSum += happiness;
        this.happinessSamples++;
    }

    /** Average of today's samples so far, or -1 if there are none yet. */
    public double getDayAverageHappiness() {
        return this.happinessSamples == 0 ? -1.0 : this.happinessSum / this.happinessSamples;
    }

    /** Returns today's average (or {@code fallback} if no samples were taken) and resets the samples. */
    public double takeDayAverageHappiness(double fallback) {
        double average = this.happinessSamples == 0 ? fallback : this.happinessSum / this.happinessSamples;
        this.happinessSum = 0.0;
        this.happinessSamples = 0;
        return average;
    }

    public double getHappinessSum() {
        return happinessSum;
    }

    public int getHappinessSamples() {
        return happinessSamples;
    }

    /**
     * Persistence codec. Enums are stored by name; {@code xp_granted_tiers} is stored as a list.
     */
    public static final Codec<VillageRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("last_checked_day").forGetter(VillageRecord::getLastCheckedDay),
            Codec.INT.fieldOf("food_stock").forGetter(VillageRecord::getFoodStock),
            Codec.INT.fieldOf("required_food_stock").forGetter(VillageRecord::getRequiredFoodStock),
            Codec.STRING.fieldOf("hunger_state").forGetter(r -> r.getHungerState().name()),
            Codec.INT.fieldOf("population_last_check").forGetter(VillageRecord::getPopulationLastCheck),
            Codec.INT.fieldOf("farmer_count_last_check").forGetter(VillageRecord::getFarmerCountLastCheck),
            Codec.STRING.fieldOf("tier").forGetter(r -> r.getTier().name()),
            Codec.DOUBLE.fieldOf("last_composite_score").forGetter(VillageRecord::getLastCompositeScore),
            Codec.STRING.listOf().optionalFieldOf("xp_granted_tiers", java.util.List.of()).forGetter(
                    r -> r.getXpGrantedTiers().stream().map(Enum::name).toList()),
            Codec.INT.optionalFieldOf("pending_bell_xp", 0).forGetter(VillageRecord::getPendingBellXp),
            // Later additions are optional so older saves still load.
            Codec.DOUBLE.optionalFieldOf("happiness_sum", 0.0).forGetter(VillageRecord::getHappinessSum),
            Codec.INT.optionalFieldOf("happiness_samples", 0).forGetter(VillageRecord::getHappinessSamples)
    ).apply(instance, (day, food, required, hunger, pop, farmers, tier, score, grantedTiers, pendingXp,
                       happySum, happySamples) -> {
        VillageRecord r = new VillageRecord();
        r.setLastCheckedDay(day);
        r.setFoodStock(food);
        r.setRequiredFoodStock(required);
        r.setHungerState(VillageHungerState.valueOf(hunger));
        r.setPopulationLastCheck(pop);
        r.setFarmerCountLastCheck(farmers);
        r.setTier(VillageTier.valueOf(tier));
        r.setLastCompositeScore(score);
        for (String name : grantedTiers) {
            try {
                r.getXpGrantedTiers().add(VillageTier.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // A tier name from an older version; skip it rather than lose the record.
            }
        }
        r.setPendingBellXp(pendingXp);
        r.happinessSum = happySum;
        r.happinessSamples = happySamples;
        return r;
    }));
}
