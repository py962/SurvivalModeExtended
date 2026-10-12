package com.terrainlab.season;

/**
 * Seasonal split of a 48,000-real-tick day/night cycle between its day and night halves, used by
 * {@link com.terrainlab.worldtime.DayLengthHandler} to slow dayTime advance. The day half takes 36,000
 * real ticks at Midsummer's centre and 12,000 at Midwinter's centre; the night half is the complement.
 */
public final class SeasonalDayLength {

    /** Total real ticks for one full dayTime cycle (0-24000), constant year-round. */
    public static final long REAL_TICKS_PER_CYCLE = 48_000L;

    /** dayTime units in each half of the cycle. */
    public static final long DAYTIME_UNITS_PER_HALF = 12_000L;

    /** Real ticks moved from the night half to the day half at the Midsummer peak (mirrored at Midwinter). */
    public static final long MAX_WARP_REAL_TICKS = 12_000L;

    private SeasonalDayLength() {
    }

    /**
     * @param yearTick a tick value already normalised into [0, Season.YEAR_LENGTH_TICKS)
     *                 (i.e. SeasonManager.yearRelativeTick's output)
     * @return real-tick shift: positive = day half gets longer (summer side),
     *         negative = night half gets longer (winter side)
     */
    public static double warpRealTicks(long yearTick) {
        double halfState = Season.STATE_LENGTH_TICKS / 2.0;
        double angle = 2.0 * Math.PI * (yearTick - halfState) / Season.YEAR_LENGTH_TICKS;
        // Negated cosine: trough at the centre of MIDWINTER, peak at the centre of MIDSUMMER.
        return -MAX_WARP_REAL_TICKS * Math.cos(angle);
    }

    /** Real-tick budget for the "day" half (dayTime in [0, 12000)) of the current cycle. */
    public static double dayHalfRealTicks(long yearTick) {
        return REAL_TICKS_PER_CYCLE / 2.0 + warpRealTicks(yearTick);
    }

    /** Real-tick budget for the "night" half (dayTime in [12000, 24000)) of the current cycle. */
    public static double nightHalfRealTicks(long yearTick) {
        return REAL_TICKS_PER_CYCLE / 2.0 - warpRealTicks(yearTick);
    }

    /**
     * Target dayTime advance rate (units per real tick) for the half of the cycle dayTimeMod is in. Always
     * in (0, 1), so it is reached by cancelling some of vanilla's +1/tick.
     *
     * @param dayTimeMod current dayTime already reduced mod 24000
     * @param yearTick   current season year-tick, for the warp lookup
     */
    public static double targetRate(long dayTimeMod, long yearTick) {
        boolean isDayHalf = dayTimeMod < DAYTIME_UNITS_PER_HALF;
        double budget = isDayHalf ? dayHalfRealTicks(yearTick) : nightHalfRealTicks(yearTick);
        return DAYTIME_UNITS_PER_HALF / budget;
    }
}
