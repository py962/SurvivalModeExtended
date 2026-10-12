package com.terrainlab.temperature;

import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;

/**
 * Per-chunk cache of sky exposure fractions, keyed by column and Y. A block change invalidates every
 * height in the affected columns (see ModSkyExposureHandler); null means not cached.
 */
public class ChunkSkyExposureCache {

    /** Upper bound on cached heights per column; the column is cleared when exceeded. */
    private static final int MAX_HEIGHTS_PER_COLUMN = 16;

    private final Int2DoubleOpenHashMap[] columns = new Int2DoubleOpenHashMap[256];

    private static int index(int localX, int localZ) {
        return (localX & 15) << 4 | (localZ & 15);
    }

    public Double get(int localX, int y, int localZ) {
        Int2DoubleOpenHashMap column = columns[index(localX, localZ)];
        if (column == null || !column.containsKey(y)) {
            return null;
        }
        return column.get(y);
    }

    public void set(int localX, int y, int localZ, double value) {
        int index = index(localX, localZ);
        Int2DoubleOpenHashMap column = columns[index];
        if (column == null) {
            column = new Int2DoubleOpenHashMap();
            columns[index] = column;
        } else if (column.size() >= MAX_HEIGHTS_PER_COLUMN && !column.containsKey(y)) {
            column.clear();
        }
        column.put(y, value);
    }

    public void invalidate(int localX, int localZ) {
        columns[index(localX, localZ)] = null;
    }
}
