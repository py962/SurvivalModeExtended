package com.terrainlab.event;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.temperature.ChunkSkyExposureCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Invalidates the chunk sky-exposure cache on block changes. Sampling reads a 3x3 grid of
 * columns, so all nine columns around the changed one are invalidated.
 *
 * <p>Invalidation is chunk-local: at chunk borders the neighbouring chunk's columns are not
 * invalidated and may stay stale.
 */
public class ModSkyExposureHandler {

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        ChunkAccess chunk = level.getChunk(pos);
        ChunkSkyExposureCache cache = chunk.getData(ModAttachments.CHUNK_SKY_EXPOSURE_CACHE.get());

        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = localX + dx;
                int nz = localZ + dz;
                if (nx < 0 || nx > 15 || nz < 0 || nz > 15) continue; // chunk-local only, see class doc
                cache.invalidate(nx, nz);
            }
        }
    }
}
