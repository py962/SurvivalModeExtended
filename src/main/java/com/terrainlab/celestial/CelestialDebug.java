package com.terrainlab.celestial;

import com.terrainlab.TerrainLab;

/**
 * Master switch for Celestial conduit/amulet diagnostic logging. Call sites check
 * {@link #VERBOSE} before logging so the varargs array is not allocated when it is off.
 */
public final class CelestialDebug {

    public static final boolean VERBOSE = false;

    private CelestialDebug() {
    }

    public static void log(String message, Object... args) {
        TerrainLab.LOGGER.info("[CELESTIAL] " + message, args);
    }
}
