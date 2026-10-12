package com.terrainlab.client;

import com.terrainlab.temperature.HeatSourceIndex;

/**
 * Client-side mirror of the server's per-level {@link HeatSourceIndex}, kept in sync by
 * HeatSourceSyncPacket and HeatSourceRemovePacket. A single global instance is enough
 * because the client only has one level loaded at a time.
 */
public final class ClientHeatSourceIndex {

    private static final HeatSourceIndex INSTANCE = new HeatSourceIndex();

    private ClientHeatSourceIndex() {
    }

    public static HeatSourceIndex get() {
        return INSTANCE;
    }
}
