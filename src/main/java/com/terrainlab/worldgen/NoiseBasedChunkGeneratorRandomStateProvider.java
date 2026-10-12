package com.terrainlab.worldgen;

import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

/**
 * Exposes the RandomState that BandedSeaLevelMixin caches on NoiseBasedChunkGenerator to non-mixin code
 * (e.g. InlandRiverFloodFeature). Implemented by that mixin; kept outside the mixin package because classes
 * there cannot be referenced directly.
 */
public interface NoiseBasedChunkGeneratorRandomStateProvider {
    @Nullable RandomState terrainlab$getRandomState();
}
