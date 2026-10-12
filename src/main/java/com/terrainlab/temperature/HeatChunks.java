package com.terrainlab.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * Non-blocking "is this chunk loaded?" checks for the heat system; never loads or generates chunks.
 * <p>
 * Level#getBlockState on an unloaded chunk makes the server load or generate it synchronously, so every heat
 * read that may fall outside the loaded area goes through here first. On the server this uses
 * ServerChunkCache#getChunkNow (FULL chunks only, null off the server thread) rather than LevelReader#hasChunk,
 * which only checks the ticket level and can still block. On the client getChunkNow is a plain lookup.
 */
public final class HeatChunks {

    private HeatChunks() {
    }

    public static boolean isLoaded(LevelReader level, int chunkX, int chunkZ) {
        if (level instanceof Level fullLevel) {
            return fullLevel.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
        }
        return level.hasChunk(chunkX, chunkZ);
    }

    public static boolean isLoaded(LevelReader level, BlockPos pos) {
        return isLoaded(level, SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    /** True if every chunk overlapping the block-coordinate rectangle is loaded. */
    public static boolean areaLoaded(LevelReader level, int minX, int minZ, int maxX, int maxZ) {
        int minCX = SectionPos.blockToSectionCoord(minX);
        int maxCX = SectionPos.blockToSectionCoord(maxX);
        int minCZ = SectionPos.blockToSectionCoord(minZ);
        int maxCZ = SectionPos.blockToSectionCoord(maxZ);
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!isLoaded(level, cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }
}
