package com.terrainlab.temperature;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Cached sky-exposure lookup: reads the per-chunk position cache and fills it from {@link SkyExposureSampler} on a miss.
 * The cache is server-only because invalidation runs on server block updates; client lookups sample directly.
 */
public final class SkyExposureService {

    private SkyExposureService() {
    }

    public static double getExposure(LevelReader level, BlockPos pos) {
        if (level.isClientSide()) {
            return SkyExposureSampler.getExposure(level, pos);
        }

        ChunkAccess chunk = level.getChunk(pos);
        ChunkSkyExposureCache cache = chunk.getData(ModAttachments.CHUNK_SKY_EXPOSURE_CACHE.get());

        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;

        Double cached = cache.get(localX, pos.getY(), localZ);
        if (cached != null) {
            return cached;
        }

        double computed = SkyExposureSampler.getExposure(level, pos);
        cache.set(localX, pos.getY(), localZ, computed);
        return computed;
    }
}
