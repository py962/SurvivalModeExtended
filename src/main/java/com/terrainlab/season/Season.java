package com.terrainlab.season;

/**
 * The six calendar states of the seasonal year. Each lasts 10 Minecraft days (240,000 ticks), giving a
 * 60-day year.
 */
public enum Season {
    MIDWINTER(TemperatureSeason.MIDWINTER, VisualSeason.WINTER),
    EARLY_SPRING(TemperatureSeason.NEAR_WINTER, VisualSeason.SPRING),
    LATE_SPRING(TemperatureSeason.NEAR_SUMMER, VisualSeason.SPRING),
    MIDSUMMER(TemperatureSeason.MIDSUMMER, VisualSeason.SUMMER),
    EARLY_AUTUMN(TemperatureSeason.NEAR_SUMMER, VisualSeason.AUTUMN),
    LATE_AUTUMN(TemperatureSeason.NEAR_WINTER, VisualSeason.AUTUMN);

    /** Ticks per calendar state: 10 Minecraft days. */
    public static final long STATE_LENGTH_TICKS = 240_000L;

    /** Number of calendar states in a full year. */
    public static final int STATE_COUNT = values().length;

    /** Full seasonal year length in ticks: 60 Minecraft days. */
    public static final long YEAR_LENGTH_TICKS = STATE_LENGTH_TICKS * STATE_COUNT;

    private final TemperatureSeason temperatureSeason;
    private final VisualSeason visualSeason;

    Season(TemperatureSeason temperatureSeason, VisualSeason visualSeason) {
        this.temperatureSeason = temperatureSeason;
        this.visualSeason = visualSeason;
    }

    public TemperatureSeason temperatureSeason() {
        return temperatureSeason;
    }

    public VisualSeason visualSeason() {
        return visualSeason;
    }

    /**
     * Resolves the calendar state from a seasonal-year-relative tick count.
     *
     * @param yearTick a tick value already normalised into [0, YEAR_LENGTH_TICKS)
     */
    public static Season fromYearTick(long yearTick) {
        int index = (int) (yearTick / STATE_LENGTH_TICKS);
        if (index >= STATE_COUNT) {
            index = STATE_COUNT - 1;
        }
        return values()[index];
    }

    /**
     * Ticks elapsed since this calendar state began, given a year-relative tick count.
     */
    public static long ticksIntoCurrentState(long yearTick) {
        return yearTick % STATE_LENGTH_TICKS;
    }

    public Season next() {
        return values()[(this.ordinal() + 1) % STATE_COUNT];
    }
}
