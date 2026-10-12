package com.terrainlab.temperature;

/**
 * Applies sky exposure to an outdoor (biome + altitude) temperature. Full shelter moves the temperature
 * SHADE_BLEND_FRACTION of the way toward NEUTRAL_REFERENCE, so harsh biomes still need heat sources;
 * exposed positions gain or lose SUN_BONUS by day or night unless it is raining or the dimension has no
 * sky light.
 * <pre>
 * final = outdoor + (1 - exposure) * SHADE_BLEND_FRACTION * (NEUTRAL_REFERENCE - outdoor) + exposure * sunDelta
 * </pre>
 */
public final class SkyExposureEffect {

    private static final double NEUTRAL_REFERENCE = 20.0; // degrees C

    /** Fraction of the gap to NEUTRAL_REFERENCE closed by full shelter (exposure 0). */
    public static final double SHADE_BLEND_FRACTION = 0.5;

    public static final double SUN_BONUS = 5.0; // degrees C

    private SkyExposureEffect() {
    }

    public static double apply(double outdoorTemp, double exposure, boolean isDay,
                               boolean isRaining, boolean hasSkyLight) {
        return apply(outdoorTemp, exposure, isDay ? 0.0 : 1.0, isRaining, hasSkyLight);
    }

    /** As above, with the sun/night delta blended by nightFraction (+SUN_BONUS at 0, -SUN_BONUS at 1). */
    public static double apply(double outdoorTemp, double exposure, double nightFraction,
                               boolean isRaining, boolean hasSkyLight) {
        double sunDelta = (isRaining || !hasSkyLight) ? 0.0 : SUN_BONUS * (1.0 - 2.0 * nightFraction);

        double shadeFraction = 1.0 - Math.max(0.0, Math.min(1.0, exposure));
        double shadeShift = shadeFraction * SHADE_BLEND_FRACTION * (NEUTRAL_REFERENCE - outdoorTemp);

        return outdoorTemp + shadeShift + exposure * sunDelta;
    }
}
