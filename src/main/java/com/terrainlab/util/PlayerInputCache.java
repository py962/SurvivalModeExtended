package com.terrainlab.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-player float array cache keyed by player UUID. */
public class PlayerInputCache {
    public static final Map<UUID, float[]> cache = new HashMap<>();
}
