package com.terrainlab.mixin;

import com.mojang.logging.LogUtils;
import com.terrainlab.worldgen.BandedFluidPicker;
import com.terrainlab.worldgen.NoiseBasedChunkGeneratorRandomStateProvider;
import com.terrainlab.worldgen.SeaLevelRandomStateHolder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Supplier;

/**
 * Replaces the aquifer fluid picker in {@code createNoiseChunk} and {@code iterateNoiseColumn}
 * with a {@link BandedFluidPicker}, and caches the generator's RandomState for later callers.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class BandedSeaLevelMixin implements NoiseBasedChunkGeneratorRandomStateProvider {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/SeaLevelBanding]";

    @Shadow
    private Holder<NoiseGeneratorSettings> settings;

    @Unique
    private @Nullable RandomState terrainlab$cachedRandomState;

    @Unique
    private @Nullable RandomState terrainlab$cachedPickerState;

    @Unique
    private @Nullable BandedFluidPicker terrainlab$cachedPicker;

    @Redirect(
            method = "createNoiseChunk",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"
            ),
            require = 1
    )
    private Object terrainlab$redirectFluidPickerInCreateNoiseChunk(
            Supplier<Aquifer.FluidPicker> supplier,
            ChunkAccess chunkAccess,
            StructureManager structureManager,
            Blender blender,
            RandomState randomState
    ) {
        if (!this.terrainlab$isOverworldSettings()) {
            return supplier.get();
        }
        this.terrainlab$cacheRandomState(randomState);
        return this.terrainlab$pickerFor(randomState);
    }

    @Redirect(
            method = "iterateNoiseColumn",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"
            ),
            require = 1
    )
    private Object terrainlab$redirectFluidPickerInIterateNoiseColumn(
            Supplier<Aquifer.FluidPicker> supplier,
            net.minecraft.world.level.LevelHeightAccessor levelHeightAccessor,
            RandomState randomState,
            int x,
            int z,
            org.apache.commons.lang3.mutable.MutableObject<?> mutableObject,
            java.util.function.Predicate<?> predicate
    ) {
        if (!this.terrainlab$isOverworldSettings()) {
            return supplier.get();
        }
        this.terrainlab$cacheRandomState(randomState);
        return this.terrainlab$pickerFor(randomState);
    }

    /** Banding applies only to overworld-style generators; the Nether and End keep vanilla fluids. */
    @Unique
    private boolean terrainlab$isOverworldSettings() {
        return this.settings.is(NoiseGeneratorSettings.OVERWORLD)
                || this.settings.is(NoiseGeneratorSettings.LARGE_BIOMES)
                || this.settings.is(NoiseGeneratorSettings.AMPLIFIED);
    }

    /**
     * Returns the picker for this RandomState, rebuilding it only when the RandomState instance
     * changes (identity comparison). The picker is shared across chunk-generation worker threads,
     * so its column cache must be thread-safe.
     */
    @Unique
    private BandedFluidPicker terrainlab$pickerFor(RandomState randomState) {
        BandedFluidPicker cached = this.terrainlab$cachedPicker;
        if (cached != null && this.terrainlab$cachedPickerState == randomState) {
            return cached;
        }
        BandedFluidPicker picker = BandedFluidPicker.fromRandomState(randomState, this.settings.value());
        this.terrainlab$cachedPickerState = randomState;
        this.terrainlab$cachedPicker = picker;
        return picker;
    }

    @Unique
    private void terrainlab$cacheRandomState(RandomState randomState) {
        if (this.terrainlab$cachedRandomState == null) {
            this.terrainlab$cachedRandomState = randomState;
            // LOGGER.info("{} RandomState cached for the first time this world/dimension load - now available to non-mixin callers via terrainlab$getRandomState()",
            //         LOG_PREFIX);
        }
        // Mirrored into a static holder for SeaLevelBarrierSuppressionMixin, which has no generator
        // reference; always updated so it tracks the most recent generator.
        SeaLevelRandomStateHolder.set(randomState);
    }

    @Override
    @Unique
    public @Nullable RandomState terrainlab$getRandomState() {
        if (this.terrainlab$cachedRandomState == null) {
            LOGGER.warn("{} terrainlab$getRandomState() called before RandomState was cached - caller will receive null and should fail safe",
                    LOG_PREFIX);
        }
        return this.terrainlab$cachedRandomState;
    }
}
