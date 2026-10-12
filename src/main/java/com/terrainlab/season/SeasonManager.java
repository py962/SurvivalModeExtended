package com.terrainlab.season;

/**
 * Seasonal clock math: a pure function of (gameTime, seasonalOffset) to calendar state, independent of
 * SavedData.
 */
public final class SeasonManager {

    private SeasonManager() {
    }

    /**
     * @param levelGameTime the world's raw game time (e.g. ServerLevel#getGameTime())
     * @param seasonalOffset a saved per-world offset, added to game time before the
     *                       modulo, so worlds don't all start on the same season
     */
    public static long yearRelativeTick(long levelGameTime, long seasonalOffset) {
        long total = levelGameTime + seasonalOffset;
        long mod = total % Season.YEAR_LENGTH_TICKS;
        return mod < 0 ? mod + Season.YEAR_LENGTH_TICKS : mod;
    }

    public static Season getCurrentSeason(long levelGameTime, long seasonalOffset) {
        return Season.fromYearTick(yearRelativeTick(levelGameTime, seasonalOffset));
    }

    public static TemperatureSeason getCurrentTemperatureSeason(long levelGameTime, long seasonalOffset) {
        return getCurrentSeason(levelGameTime, seasonalOffset).temperatureSeason();
    }

    public static VisualSeason getCurrentVisualSeason(long levelGameTime, long seasonalOffset) {
        return getCurrentSeason(levelGameTime, seasonalOffset).visualSeason();
    }

    /**
     * Progress through the current 10-day calendar state, as a fraction [0.0, 1.0).
     * Useful for cross-fading visuals or interpolating toward the next state.
     */
    public static double getStateProgress(long levelGameTime, long seasonalOffset) {
        long yearTick = yearRelativeTick(levelGameTime, seasonalOffset);
        long intoState = Season.ticksIntoCurrentState(yearTick);
        return intoState / (double) Season.STATE_LENGTH_TICKS;
    }

    /**
     * Which in-game day number (0-9) within the current 10-day calendar state.
     */
    public static int getDayInState(long levelGameTime, long seasonalOffset) {
        long yearTick = yearRelativeTick(levelGameTime, seasonalOffset);
        long intoState = Season.ticksIntoCurrentState(yearTick);
        return (int) (intoState / 24_000L); // 24,000 ticks per day
    }
}
