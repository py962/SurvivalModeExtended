package com.terrainlab.debug;

/**
 * One switch for TerrainLab's diagnostic logging: start the game or server with the JVM argument
 * {@code -Dterrainlab.debug=true} (IntelliJ: Run configuration, VM options). Off by default, so
 * players never see it. Lines are logged at INFO with a [TerrainLab/...] prefix.
 */
public final class TerrainLabDebug {

    public static final boolean ENABLED = Boolean.getBoolean("terrainlab.debug");

    private TerrainLabDebug() {
    }
}
