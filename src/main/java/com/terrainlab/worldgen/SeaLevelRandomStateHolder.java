package com.terrainlab.worldgen;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

/**
 * Global holder for the RandomState BandedSeaLevelMixin caches, for code with no generator reference
 * (Aquifer.NoiseBasedAquifer in SeaLevelBarrierSuppressionMixin). Last writer wins; banding is
 * Overworld-only, so another dimension's write can only leave a briefly stale value.
 */
public final class SeaLevelRandomStateHolder {

    private static volatile @Nullable RandomState cachedRandomState;

    // 26.x: RandomState no longer exposes its NoiseRouter, so the (unseeded) router from the generator settings
    // is mirrored here too; sample it through RandomState.getSampler. Set by BandedFluidPicker.fromRandomState.
    private static volatile @Nullable NoiseRouter cachedNoiseRouter;

    private SeaLevelRandomStateHolder() {
    }

    public static void set(RandomState randomState) {
        cachedRandomState = randomState;
    }

    public static @Nullable RandomState get() {
        return cachedRandomState;
    }

    public static void setNoiseRouter(NoiseRouter noiseRouter) {
        cachedNoiseRouter = noiseRouter;
    }

    public static @Nullable NoiseRouter getNoiseRouter() {
        return cachedNoiseRouter;
    }
}
