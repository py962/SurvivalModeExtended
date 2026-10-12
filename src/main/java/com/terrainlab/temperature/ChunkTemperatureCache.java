package com.terrainlab.temperature;

import com.terrainlab.season.SeasonalBiomeData;

/**
 * Per-chunk cache of blended (pre-altitude) SeasonalBiomeData, one slot per column. Filled lazily and never
 * invalidated; altitude is applied at read time.
 */
public class ChunkTemperatureCache {

    private final SeasonalBiomeData[] columns = new SeasonalBiomeData[256];

    private static int index(int localX, int localZ) {
        return (localX & 15) << 4 | (localZ & 15);
    }

    public SeasonalBiomeData get(int localX, int localZ) {
        return columns[index(localX, localZ)];
    }

    public void set(int localX, int localZ, SeasonalBiomeData data) {
        columns[index(localX, localZ)] = data;
    }
}
