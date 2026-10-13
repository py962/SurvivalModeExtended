package com.terrainlab.temperature;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Level-scoped index of heat sources in loaded chunks, bucketed by chunk so queries only touch nearby
 * chunks. HeatSourceIndexHandler removes a chunk's sources on unload and rebuilds them on load; the index
 * is not saved. Synchronized because an integrated client reads its mirror on the render thread.
 */
public class HeatSourceIndex {

    private final Long2ObjectOpenHashMap<Map<BlockPos, HeatSourceData>> byChunk = new Long2ObjectOpenHashMap<>();

    private static long key(BlockPos pos) {
        return ChunkPos.pack(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /** Adds or replaces a heat source. */
    public synchronized void put(BlockPos pos, HeatSourceData data) {
        BlockPos key = pos.immutable();
        long chunkKey = key(key);
        Map<BlockPos, HeatSourceData> bucket = byChunk.get(chunkKey);
        if (bucket == null) {
            bucket = new HashMap<>();
            byChunk.put(chunkKey, bucket);
        }
        bucket.put(key, data);
    }

    /** Removes a heat source if present; returns whether one was there. */
    public synchronized boolean remove(BlockPos pos) {
        long k = key(pos);
        Map<BlockPos, HeatSourceData> bucket = byChunk.get(k);
        if (bucket == null || bucket.remove(pos) == null) {
            return false;
        }
        if (bucket.isEmpty()) {
            byChunk.remove(k);
        }
        return true;
    }

    public synchronized HeatSourceData get(BlockPos pos) {
        Map<BlockPos, HeatSourceData> bucket = byChunk.get(key(pos));
        return bucket == null ? null : bucket.get(pos);
    }

    public synchronized boolean contains(BlockPos pos) {
        return get(pos) != null;
    }

    /**
     * Stores {@code updated} only if the entry is still {@code expected}, so an asynchronously computed
     * enclosure never resurrects or overwrites a source changed in the meantime.
     */
    public synchronized void replaceIfUnchanged(HeatSourceData expected, HeatSourceData updated) {
        Map<BlockPos, HeatSourceData> bucket = byChunk.get(key(expected.sourcePos()));
        if (bucket != null && bucket.get(expected.sourcePos()) == expected) {
            bucket.put(expected.sourcePos(), updated);
        }
    }

    /**
     * Snapshot of every source within {@code radius} blocks (straight-line, as HeatSourceData#contributionAt).
     */
    public synchronized List<HeatSourceData> near(BlockPos pos, int radius) {
        List<HeatSourceData> result = new ArrayList<>();
        double maxDistSqr = (double) radius * radius;
        int minCX = SectionPos.blockToSectionCoord(pos.getX() - radius);
        int maxCX = SectionPos.blockToSectionCoord(pos.getX() + radius);
        int minCZ = SectionPos.blockToSectionCoord(pos.getZ() - radius);
        int maxCZ = SectionPos.blockToSectionCoord(pos.getZ() + radius);
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                Map<BlockPos, HeatSourceData> bucket = byChunk.get(ChunkPos.pack(cx, cz));
                if (bucket == null) {
                    continue;
                }
                for (HeatSourceData data : bucket.values()) {
                    if (data.sourcePos().distSqr(pos) <= maxDistSqr) {
                        result.add(data);
                    }
                }
            }
        }
        return result;
    }

    /** Snapshot of one chunk's sources. */
    public synchronized List<HeatSourceData> inChunk(int chunkX, int chunkZ) {
        Map<BlockPos, HeatSourceData> bucket = byChunk.get(ChunkPos.pack(chunkX, chunkZ));
        return bucket == null ? List.of() : new ArrayList<>(bucket.values());
    }

    /** Drops one chunk's sources (chunk unloaded). */
    public synchronized void removeChunk(int chunkX, int chunkZ) {
        byChunk.remove(ChunkPos.pack(chunkX, chunkZ));
    }

    /**
     * Clears the cached enclosure of every source within HeatFloodFill.RADIUS (Chebyshev) of a changed block
     * and returns the updated entries for client sync.
     */
    public synchronized List<HeatSourceData> invalidateEnclosureNear(BlockPos changed) {
        List<HeatSourceData> updated = new ArrayList<>();
        int r = HeatFloodFill.RADIUS;
        int minCX = SectionPos.blockToSectionCoord(changed.getX() - r);
        int maxCX = SectionPos.blockToSectionCoord(changed.getX() + r);
        int minCZ = SectionPos.blockToSectionCoord(changed.getZ() - r);
        int maxCZ = SectionPos.blockToSectionCoord(changed.getZ() + r);
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                Map<BlockPos, HeatSourceData> bucket = byChunk.get(ChunkPos.pack(cx, cz));
                if (bucket == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, HeatSourceData> entry : bucket.entrySet()) {
                    BlockPos s = entry.getKey();
                    if (Math.abs(s.getX() - changed.getX()) > r
                            || Math.abs(s.getY() - changed.getY()) > r
                            || Math.abs(s.getZ() - changed.getZ()) > r
                            || s.equals(changed)
                            || !entry.getValue().hasEnclosure()) {
                        continue;
                    }
                    HeatSourceData cleared = entry.getValue().withoutEnclosure();
                    entry.setValue(cleared);
                    updated.add(cleared);
                }
            }
        }
        return updated;
    }

    /** Flat snapshot of the whole index; prefer {@link #near} or {@link #inChunk}. */
    public synchronized Map<BlockPos, HeatSourceData> all() {
        Map<BlockPos, HeatSourceData> copy = new HashMap<>();
        for (Map<BlockPos, HeatSourceData> bucket : byChunk.values()) {
            copy.putAll(bucket);
        }
        return copy;
    }
}
