package com.terrainlab.village;

/**
 * The four village tiers, ordered by {@link #level()}. Constant names are persisted in
 * VillageRecord, so renaming them breaks existing saves.
 */
public enum VillageTier {
    TIER_1_OUTPOST(1),
    TIER_2_HAMLET(2),
    TIER_3_VILLAGE(3),
    TIER_4_TOWN(4);

    private final int level;

    VillageTier(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }

    public VillageTier next() {
        return level < 4 ? values()[level] : this;
    }

    public VillageTier previous() {
        return level > 1 ? values()[level - 2] : this;
    }
}
