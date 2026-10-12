package com.terrainlab.season;

/**
 * Rendering-independent math for the Calendar item's dial: needle rotation and current wedge, derived
 * from game time and the world's seasonal offset.
 */
public final class CalendarDial {

    private CalendarDial() {
    }

    /** Needle rotation as a fraction of a full turn [0, 1); one turn per seasonal year. */
    public static float getNeedleRotation(long levelGameTime, long seasonalOffset) {
        long yearTick = SeasonManager.yearRelativeTick(levelGameTime, seasonalOffset);
        return (float) (yearTick / (double) Season.YEAR_LENGTH_TICKS);
    }

    /** Wedge the needle is in; the dial texture's wedge order must match {@link Season} declaration order. */
    public static Season getCurrentWedge(long levelGameTime, long seasonalOffset) {
        return SeasonManager.getCurrentSeason(levelGameTime, seasonalOffset);
    }
}
