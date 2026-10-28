package com.terrainlab.season;

import com.terrainlab.TerrainLab;
import net.minecraft.world.level.Level;

import java.util.function.LongSupplier;

/**
 * Seasonal day length by warping time-of-day ("solar time") for sun-dependent systems, without touching
 * the tick clock. Only the overworld warps.
 */
public final class SolarTime {

    private SolarTime() {
    }

    /** Peak seasonal strength: +/-0.4 is roughly 10h winter / 16h summer daylight. */
    public static final double MAX_K = 0.4;

    private static final double TWO_PI = Math.PI * 2.0;

    /** Vanilla's sleep window (unwarped): beds work from 12542 until 23460. */
    public static final long VANILLA_SLEEP_START = 12542L;
    public static final long VANILLA_SLEEP_END = 23460L;

    /**
     * Bed window on the unwarped clock: 8pm to 4am, roughly midsummer sunset to sunrise. The rest of the
     * year the sun sets before and rises after it, leaving dark time either side for mobs to spawn.
     */
    public static final long BED_START = 14000L;
    public static final long BED_END = 22000L;

    // Debug trace state (server side only).
    private static long lastDebugDay = Long.MIN_VALUE;
    private static long lastDebugBucket = Long.MIN_VALUE;

    private static long lastObservedDayTime = Long.MIN_VALUE;
    private static long lastObservedSolarTicks = Long.MIN_VALUE;

    /** Only the overworld has seasons (and a moving sun). */
    public static boolean warps(Level level) {
        return level.dimension() == Level.OVERWORLD
                && !level.dimensionType().hasFixedTime();
    }

    /** Seasonal strength k in [-MAX_K, MAX_K]; positive in summer (longer days). */
    public static double k(Level level) {
        double year = SeasonBridge.yearFraction(level);

        return MAX_K * Math.cos(
                TWO_PI * (year - SeasonBridge.midsummerFraction())
        );
    }

    /**
     * Warped ("solar") time for an absolute dayTime. The day component is taken from the raw clock, so the
     * warped time-of-day can cross the 0/24000 boundary.
     */
    public static long solarTicks(Level level, long dayTime) {
        double seasonalK = k(level);

        long day = Math.floorDiv(dayTime, 24000L);
        long t = Math.floorMod(dayTime, 24000L);

        double amplitude = seasonalK * 24000.0 / TWO_PI;

        double angle =
                TWO_PI * (t - 6000.0) / 24000.0;

        double sine = Math.sin(angle);

        double warp = amplitude * sine;

        double s = t - warp;

        long solar = day * 24000L + Math.round(s);

        terrainlab$debug(
                level,
                dayTime,
                day,
                t,
                seasonalK,
                amplitude,
                angle,
                sine,
                warp,
                solar
        );

        return solar;
    }

    /** Debug trace: logs raw (F3) vs solar time once per 1000-tick bucket and on raw clock discontinuities. */
    private static void terrainlab$debug(
            Level level,
            long dayTime,
            long day,
            long t,
            double seasonalK,
            double amplitude,
            double angle,
            double sine,
            double warp,
            long solar
    ) {
        if (level.isClientSide()) {
            return;
        }

        long solarDay = Math.floorDiv(solar, 24000L);
        long solarTOD = Math.floorMod(solar, 24000L);

        if (lastObservedDayTime != Long.MIN_VALUE) {

            long rawDelta = dayTime - lastObservedDayTime;

            boolean largeJump =
                    rawDelta > 100L
                            || rawDelta < -100L;

            boolean backwardsJump =
                    rawDelta < -100L;

            if (largeJump) {
                long solarDelta =
                        solar - lastObservedSolarTicks;

                // TerrainLab.LOGGER.debug(
                        // "[SolarTime SYNC DEBUG] RAW CLOCK DISCONTINUITY!"
                                // + " F3_DAY_PREVIOUS={} F3_TIME_PREVIOUS={}"
                                // + " F3_DAY_CURRENT={} F3_TIME_CURRENT={}"
                                // + " F3_TIME_DELTA={}"
                                // + " solarPrevious={} solarCurrent={} solarDelta={}"
                                // + " solarPreviousTOD={} solarCurrentTOD={}"
                                // + " solarPreviousDay={} solarCurrentDay={}"
                                // + " k={}"
                                // + " reason={}",
                        // Math.floorDiv(lastObservedDayTime, 24000L),
                        // Math.floorMod(lastObservedDayTime, 24000L),
                        // day,
                        // t,
                        // rawDelta,
                        // lastObservedSolarTicks,
                        // solar,
                        // solarDelta,
                        // Math.floorMod(lastObservedSolarTicks, 24000L),
                        // solarTOD,
                        // Math.floorDiv(lastObservedSolarTicks, 24000L),
                        // solarDay,
                        // seasonalK,
                        // backwardsJump
                                // ? "F3 TIME MOVED BACKWARDS"
                                // : "F3 TIME JUMPED FORWARDS"
                // );
            }
        }

        lastObservedDayTime = dayTime;
        lastObservedSolarTicks = solar;

        long bucket = t / 1000L;

        boolean importantPoint =
                t == 0L
                        || t == 1000L
                        || t == 6000L
                        || t == 9000L
                        || t == 12000L
                        || t == 18000L
                        || t == 23999L;

        boolean newBucket =
                day != lastDebugDay
                        || bucket != lastDebugBucket;

        if (!importantPoint && !newBucket) {
            return;
        }

        lastDebugDay = day;
        lastDebugBucket = bucket;

        // TerrainLab.LOGGER.debug(
                // "[SolarTime DEBUG]"
                        // + " F3_DAY={} F3_TIME={}"
                        // + " k={}"
                        // + " amplitude={}"
                        // + " angle={}"
                        // + " sine={}"
                        // + " warp={}"
                        // + " solar={} solarDay={} solarTOD={}"
                        // + " offset={}",
                // day,
                // t,
                // seasonalK,
                // amplitude,
                // angle,
                // sine,
                // warp,
                // solar,
                // solarDay,
                // solarTOD,
                // solar - dayTime
        // );
    }

    /** Solar time of day, 0..23999. */
    public static long solarTimeOfDay(Level level) {
        return Math.floorMod(
                solarTicks(level, level.getDayTime()),
                24000L
        );
    }

    /** Day by the warped sun. */
    public static boolean isSolarDay(Level level) {
        if (!warps(level)) {
            return Math.floorMod(level.getDayTime(), 24000L) < 12000L;
        }

        return solarTimeOfDay(level) < 12000L;
    }

    /**
     * Length (ticks) of the temperature transition centred on solar sunset and sunrise: 2000 ticks is
     * 100 s, about two in-game hours.
     */
    public static final long TEMPERATURE_TRANSITION_TICKS = 2000L;

    /**
     * How far into "night" the temperature is, 0 (full day) to 1 (full night). Ramps smoothly across
     * TEMPERATURE_TRANSITION_TICKS centred on solar sunset (12000) and sunrise (0/24000), so day and
     * night temperatures blend instead of switching at once. Uses the warped sun, like isSolarDay.
     */
    public static double nightFraction(Level level) {
        long s = warps(level)
                ? solarTimeOfDay(level)
                : Math.floorMod(level.getDayTime(), 24000L);
        long half = TEMPERATURE_TRANSITION_TICKS / 2L;

        double linear;
        if (s >= 12000L - half && s < 12000L + half) {
            // Dusk: day -> night.
            linear = (s - (12000L - half)) / (double) TEMPERATURE_TRANSITION_TICKS;
        } else if (s >= 12000L + half && s < 24000L - half) {
            linear = 1.0;
        } else if (s >= 24000L - half || s < half) {
            // Dawn: night -> day, wrapping past 0.
            long u = Math.floorMod(s - (24000L - half), 24000L);
            linear = 1.0 - u / (double) TEMPERATURE_TRANSITION_TICKS;
        } else {
            linear = 0.0;
        }
        return linear * linear * (3.0 - 2.0 * linear); // smoothstep
    }

    /** Day counter that rolls over at solar time 0, so a whole night shares one number. */
    public static long solarDayNumber(Level level) {
        if (!warps(level)) {
            return Math.floorDiv(level.getDayTime(), 24000L);
        }
        return Math.floorDiv(solarTicks(level, level.getDayTime()), 24000L);
    }

    /** The first stretch of the night after solar sunset. */
    public static boolean isSolarDusk(Level level, long length) {
        long s = warps(level)
                ? solarTimeOfDay(level)
                : Math.floorMod(level.getDayTime(), 24000L);
        return s >= 12000L && s < 12000L + length;
    }

    /** Night by the warped sun. */
    public static boolean isSolarNight(Level level) {
        long s = solarTimeOfDay(level);

        return s >= VANILLA_SLEEP_START
                && s < VANILLA_SLEEP_END;
    }

    /** Vanilla's unwarped sleep window. */
    /** True during the 8pm-4am bed window (unwarped overworld clock). */
    public static boolean isBedTime(Level level) {
        long t = Math.floorMod(level.getDayTime(), 24000L);
        return t >= BED_START && t < BED_END;
    }

    /** Clock handed to warped timelines. */
    public record SolarClock(Level level, LongSupplier raw)
            implements LongSupplier {

        @Override
        public long getAsLong() {
            long rawTime = raw.getAsLong();
            long solarTime = solarTicks(level, rawTime);

            return solarTime;
        }
    }
}
