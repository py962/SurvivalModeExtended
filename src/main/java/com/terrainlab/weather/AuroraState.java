package com.terrainlab.weather;

/**
 * Server-side aurora state for one dimension. Deterministic: active whenever the trigger conditions
 * (night, cold biome) hold.
 */
public record AuroraState(boolean active, long activeSince) {

    public static final AuroraState INACTIVE = new AuroraState(false, -1L);

    public static AuroraState activate(long gameTime) {
        return new AuroraState(true, gameTime);
    }

    public static AuroraState deactivate() {
        return INACTIVE;
    }

    public long durationTicks(long gameTime) {
        if (!active) return 0L;
        return Math.max(0L, gameTime - activeSince);
    }
}
