package com.terrainlab.worldgen;

import com.terrainlab.TerrainLab;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-column vertical span of aquifer barriers that vanilla actually placed, as observed by
 * SeaLevelBarrierSuppressionMixin during a chunk's noise fill and carving (barrier y within the banding window).
 * <ul>
 *   <li>maxY is the higher of the two water levels meeting at the wall.</li>
 *   <li>minY - 1 is the lower water level.</li>
 * </ul>
 * Data is keyed by dimension and chunk. Recording only happens between {@link #beginChunkGeneration} and
 * {@link #endChunkGeneration} on the generating thread, so aquifers built for height probes (structure
 * placement, /locate, spawn search) are ignored. SeaLevelBarrierGapFeature consumes each chunk's data with
 * {@link #takeChunk} during feature placement; a chunk's entry is also dropped once it loads as a full chunk,
 * and everything is cleared when the server stops.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class AquiferBarrierRecorder {

    private AquiferBarrierRecorder() {
    }

    /** Index into the int[] stored per column: lowest observed barrier y. */
    public static final int MIN_Y = 0;
    /** Index into the int[] stored per column: highest observed barrier y. */
    public static final int MAX_Y = 1;

    // dimension -> chunk-long -> (packed local column key -> {minY, maxY})
    private static final Map<ResourceKey<Level>, Map<Long, Map<Integer, int[]>>> BARRIER_COLUMNS = new ConcurrentHashMap<>();

    // Dimension of the chunk being filled or carved on this thread; null outside real chunk generation.
    private static final ThreadLocal<ResourceKey<Level>> GENERATING_DIMENSION = new ThreadLocal<>();

    /** Marks this thread as generating a real chunk in the given dimension. */
    public static void beginChunkGeneration(ResourceKey<Level> dimension) {
        GENERATING_DIMENSION.set(dimension);
    }

    /** Ends the scope opened by {@link #beginChunkGeneration}. */
    public static void endChunkGeneration() {
        GENERATING_DIMENSION.remove();
    }

    /**
     * Widens the column's observed {minY, maxY} span to include this barrier block. Ignored outside real chunk
     * generation. Thread-safe: worldgen runs many chunks in parallel, so updates go through
     * ConcurrentHashMap#compute.
     */
    public static void recordBarrier(int x, int y, int z) {
        ResourceKey<Level> dimension = GENERATING_DIMENSION.get();
        if (dimension == null) {
            return;
        }

        long chunkKey = ChunkPos.pack(x >> 4, z >> 4);
        int columnKey = ((x & 15) << 4) | (z & 15);

        Map<Long, Map<Integer, int[]>> chunks = BARRIER_COLUMNS.computeIfAbsent(dimension, key -> new ConcurrentHashMap<>());
        Map<Integer, int[]> columns = chunks.computeIfAbsent(chunkKey, key -> new ConcurrentHashMap<>());
        columns.compute(columnKey, (key, span) -> {
            if (span == null) {
                return new int[]{y, y};
            }
            if (y < span[MIN_Y]) {
                span[MIN_Y] = y;
            }
            if (y > span[MAX_Y]) {
                span[MAX_Y] = y;
            }
            return span;
        });
    }

    /** Removes and returns one chunk's columns: packed local-column key (see recordBarrier) to {minY, maxY}. */
    public static Map<Integer, int[]> takeChunk(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        Map<Long, Map<Integer, int[]>> chunks = BARRIER_COLUMNS.get(dimension);
        if (chunks == null) {
            return Map.of();
        }
        Map<Integer, int[]> columns = chunks.remove(ChunkPos.pack(chunkX, chunkZ));
        return columns == null ? Map.of() : columns;
    }

    /** A full chunk is past feature placement, so any data left for it is never consumed. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<Long, Map<Integer, int[]>> chunks = BARRIER_COLUMNS.get(level.dimension());
        if (chunks != null) {
            chunks.remove(event.getChunk().getPos().pack());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BARRIER_COLUMNS.clear();
    }
}
