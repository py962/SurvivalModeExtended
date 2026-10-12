package com.terrainlab.season;

import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.data.SeasonSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The season system's single entry point for {@link SolarTime}. Client and server compute the same
 * year fraction from game time plus the world's seasonal offset (server: SeasonSavedData; client:
 * ClientSeasonState, synced on login).
 * <p>
 * The server offset is cached and refreshed every REFRESH_TICKS because the solar clock is sampled on
 * every environment-attribute read. ClientSeasonState has no client-only imports, so this is safe on a
 * dedicated server.
 */
public final class SeasonBridge {

    private SeasonBridge() {
    }

    /** Centre of MIDSUMMER as a fraction of the year: the longest day. */
    private static final double MIDSUMMER_CENTRE =
            (Season.MIDSUMMER.ordinal() + 0.5) / Season.STATE_COUNT;

    private static final int REFRESH_TICKS = 100;

    private static long serverOffset = 0L;
    private static long serverOffsetCheckedAt = Long.MIN_VALUE;

    /** Continuous position in the year, 0..1. */
    public static double yearFraction(Level level) {
        long offset = level.isClientSide() ? ClientSeasonState.getSeasonalOffset() : serverOffset(level);
        long yearTick = SeasonManager.yearRelativeTick(level.getGameTime(), offset);
        return yearTick / (double) Season.YEAR_LENGTH_TICKS;
    }

    public static double midsummerFraction() {
        return MIDSUMMER_CENTRE;
    }

    private static long serverOffset(Level level) {
        long now = level.getGameTime();
        if (level instanceof ServerLevel serverLevel
                // Test the sentinel explicitly: now - Long.MIN_VALUE overflows.
                && (serverOffsetCheckedAt == Long.MIN_VALUE
                    || now - serverOffsetCheckedAt >= REFRESH_TICKS
                    || now < serverOffsetCheckedAt)) {
            // The solar clock is first sampled inside ServerLevel's constructor, before the overworld (which
            // holds the season data) exists; keep the cached value and retry on the next call.
            if (serverLevel.getServer().overworld() == null) {
                return serverOffset;
            }
            serverOffset = SeasonSavedData.get(serverLevel).getSeasonalOffset();
            serverOffsetCheckedAt = now;
        }
        return serverOffset;
    }
}
