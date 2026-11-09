package com.terrainlab.season;

/**
 * The six calendar states of the seasonal year. By default each lasts 10 Minecraft days, giving a 60-day
 * year; the year length is per world (60 to 360 days in steps of 60) and is set with
 * {@link #setYearLengthDays(int)}.
 */
public enum Season {
    MIDWINTER(TemperatureSeason.MIDWINTER, VisualSeason.WINTER),
    EARLY_SPRING(TemperatureSeason.NEAR_WINTER, VisualSeason.SPRING),
    LATE_SPRING(TemperatureSeason.NEAR_SUMMER, VisualSeason.SPRING),
    MIDSUMMER(TemperatureSeason.MIDSUMMER, VisualSeason.SUMMER),
    EARLY_AUTUMN(TemperatureSeason.NEAR_SUMMER, VisualSeason.AUTUMN),
    LATE_AUTUMN(TemperatureSeason.NEAR_WINTER, VisualSeason.AUTUMN);

    /** Number of calendar states in a full year. */
    public static final int STATE_COUNT = values().length;

    public static final long TICKS_PER_DAY = 24_000L;

    /** Year length of a world that has never had one set: six calendar states of 10 days. */
    public static final int DEFAULT_YEAR_LENGTH_DAYS = 60;
    public static final int MIN_YEAR_LENGTH_DAYS = 60;
    public static final int MAX_YEAR_LENGTH_DAYS = 360;
    public static final int YEAR_LENGTH_STEP_DAYS = 60;

    /**
     * The current world's year length in days. Set on the server when the overworld loads and on the
     * client when the world's season data arrives; a singleplayer client and server share it.
     */
    private static volatile int yearLengthDays = DEFAULT_YEAR_LENGTH_DAYS;

    public static boolean isValidYearLength(int days) {
        return days >= MIN_YEAR_LENGTH_DAYS && days <= MAX_YEAR_LENGTH_DAYS && days % YEAR_LENGTH_STEP_DAYS == 0;
    }

    public static int yearLengthDays() {
        return yearLengthDays;
    }

    /** Sets the year length; an invalid value falls back to the default. */
    public static void setYearLengthDays(int days) {
        yearLengthDays = isValidYearLength(days) ? days : DEFAULT_YEAR_LENGTH_DAYS;
    }

    /** Ticks per calendar state in the current world (10 Minecraft days by default). */
    public static long stateLengthTicks() {
        return yearLengthTicks() / STATE_COUNT;
    }

    /** Full seasonal year length in ticks in the current world (60 Minecraft days by default). */
    public static long yearLengthTicks() {
        return yearLengthDays * TICKS_PER_DAY;
    }

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
     * @param yearTick a tick value already normalised into [0, yearLengthTicks())
     */
    public static Season fromYearTick(long yearTick) {
        int index = (int) (yearTick / stateLengthTicks());
        if (index >= STATE_COUNT) {
            index = STATE_COUNT - 1;
        }
        return values()[index];
    }

    /**
     * Ticks elapsed since this calendar state began, given a year-relative tick count.
     */
    public static long ticksIntoCurrentState(long yearTick) {
        return yearTick % stateLengthTicks();
    }

    public Season next() {
        return values()[(this.ordinal() + 1) % STATE_COUNT];
    }
}
