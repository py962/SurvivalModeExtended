package com.terrainlab.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side record of each player's shift key from their latest input packet. Used instead of
 * {@code isShiftKeyDown()}, which does not stay set between input packets.
 */
public class ShiftStateCache {
    public static final Map<UUID, Boolean> shiftHeld = new ConcurrentHashMap<>();

    public static boolean isHeld(UUID playerId) {
        return Boolean.TRUE.equals(shiftHeld.get(playerId));
    }
}
