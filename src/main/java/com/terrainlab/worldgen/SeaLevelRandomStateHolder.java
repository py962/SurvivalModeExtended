package com.terrainlab.worldgen;

import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

/**
 * Global holder for the RandomState BandedSeaLevelMixin caches, for code with no generator reference
 * (Aquifer.NoiseBasedAquifer in SeaLevelBarrierSuppressionMixin). Last writer wins; banding is
 * Overworld-only, so another dimension's write can only leave a briefly stale value.
 */
public final class SeaLevelRandomStateHolder {

    private static volatile @Nullable RandomState cachedRandomState;

    private SeaLevelRandomStateHolder() {
    }

    public static void set(RandomState randomState) {
        cachedRandomState = randomState;
    }

    public static @Nullable RandomState get() {
        return cachedRandomState;
    }
}
