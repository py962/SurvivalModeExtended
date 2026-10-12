package com.terrainlab.worldgen;

/**
 * Banded sea level: columns farther inland get a higher water level, from 63 up to 75 in 0.05 steps of
 * {@code score = continentalness - erosion} starting at 0.75. Columns with continentalness below -0.1
 * always stay at 63. Both noises span roughly -1..1, so every tier is reachable.
 */
public final class SeaLevelBandingService {

    public static final int BASE_SEA_LEVEL = 63;

    /** Highest banded level; must equal the last entry of LEVELS. Not the barrier window (see BARRIER_MIN_Y). */
    public static final int MAX_BANDED_LEVEL = 75;

    /**
     * Y range the sea-level barrier system observes and edits. Wider than 63..75 because vanilla centres a
     * barrier on the average of two cells' levels plus barrier noise, so e.g. a 63/64 junction can place
     * barrier blocks at y=62.
     */
    public static final int BARRIER_MIN_Y = 50;
    public static final int BARRIER_MAX_Y = 80;

    private static final double CONTINENTALNESS_FLOOR = -0.1;

    private static final double[] THRESHOLDS = {
            0.75, 0.80, 0.85, 0.90, 0.95, 1.00, 1.05, 1.10, 1.15, 1.20, 1.25, 1.30
    };
    private static final int[] LEVELS = {
            64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75
    };

    private SeaLevelBandingService() {
    }

    /**
     * @param continentalness raw continentalness sample for a column - used only for the
     *                        hard floor gate.
     * @param combinedScore   continentalness - erosion for a column.
     * @return the banded sea level for that column, one of 63..75.
     */
    public static int bandedLevel(double continentalness, double combinedScore) {
        if (continentalness < CONTINENTALNESS_FLOOR) {
            return BASE_SEA_LEVEL;
        }

        int level = BASE_SEA_LEVEL;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (combinedScore >= THRESHOLDS[i]) {
                level = LEVELS[i];
            } else {
                break;
            }
        }
        return level;
    }

    /**
     * The two adjacent tiers meeting near this column: its own tier and whichever neighbouring tier's
     * threshold is closer to the raw score.
     *
     * @return {high, low}; equal only below the continentalness floor.
     */
    public static int[] highLow(double continentalness, double combinedScore) {
        if (continentalness < CONTINENTALNESS_FLOOR) {
            return new int[]{BASE_SEA_LEVEL, BASE_SEA_LEVEL};
        }

        // Number of thresholds satisfied, as in bandedLevel.
        int satisfied = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (combinedScore >= THRESHOLDS[i]) {
                satisfied = i + 1;
            } else {
                break;
            }
        }

        int here = (satisfied == 0) ? BASE_SEA_LEVEL : LEVELS[satisfied - 1];
        int oneStepDown = (satisfied >= 2) ? LEVELS[satisfied - 2] : BASE_SEA_LEVEL;
        int oneStepUp = (satisfied < LEVELS.length) ? LEVELS[satisfied] : here;

        int adjacent;
        if (satisfied == 0) {
            adjacent = oneStepUp; // at the floor - only "up" exists
        } else if (satisfied == LEVELS.length) {
            adjacent = oneStepDown; // at the ceiling - only "down" exists
        } else {
            double distanceToUpperThreshold = THRESHOLDS[satisfied] - combinedScore;
            double distanceToLowerThreshold = combinedScore - THRESHOLDS[satisfied - 1];
            adjacent = (distanceToUpperThreshold <= distanceToLowerThreshold) ? oneStepUp : oneStepDown;
        }

        return new int[]{Math.max(here, adjacent), Math.min(here, adjacent)};
    }
}
