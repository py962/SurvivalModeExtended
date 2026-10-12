package com.terrainlab.client;

/**
 * Client-side cache of the world's seasonal offset, set by SeasonOffsetPacket on login.
 * Needed because the season SavedData exists only on the server.
 */
public class ClientSeasonState {

    private static long seasonalOffset = 0L;

    private ClientSeasonState() {
    }

    public static long getSeasonalOffset() {
        return seasonalOffset;
    }

    public static void setSeasonalOffset(long offset) {
        seasonalOffset = offset;
    }
}
