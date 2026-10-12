package com.terrainlab.mixin;

import com.terrainlab.worldgen.AquiferBarrierRecorder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scopes AquiferBarrierRecorder to real chunk generation (noise fill and carving), so aquifers built by
 * iterateNoiseColumn for height probes are not recorded.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseFillContextMixin {

    @Inject(method = "doFill", at = @At("HEAD"), require = 1)
    private void terrainlab$beginFill(Blender blender, StructureManager structureManager, RandomState randomState,
                                      ChunkAccess chunk, int minCellY, int cellCountY,
                                      CallbackInfoReturnable<ChunkAccess> cir) {
        LevelAccessor level = ((StructureManagerAccessor) structureManager).terrainlab$getLevel();
        if (level instanceof ServerLevelAccessor serverLevelAccessor) {
            AquiferBarrierRecorder.beginChunkGeneration(serverLevelAccessor.getLevel().dimension());
        } else {
            AquiferBarrierRecorder.endChunkGeneration();
        }
    }

    @Inject(method = "doFill", at = @At("RETURN"), require = 1)
    private void terrainlab$endFill(Blender blender, StructureManager structureManager, RandomState randomState,
                                    ChunkAccess chunk, int minCellY, int cellCountY,
                                    CallbackInfoReturnable<ChunkAccess> cir) {
        AquiferBarrierRecorder.endChunkGeneration();
    }

    @Inject(method = "applyCarvers", at = @At("HEAD"), require = 1)
    private void terrainlab$beginCarvers(WorldGenRegion region, long seed, RandomState randomState,
                                         BiomeManager biomeManager, StructureManager structureManager,
                                         ChunkAccess chunk, CallbackInfo ci) {
        AquiferBarrierRecorder.beginChunkGeneration(region.getLevel().dimension());
    }

    @Inject(method = "applyCarvers", at = @At("RETURN"), require = 1)
    private void terrainlab$endCarvers(WorldGenRegion region, long seed, RandomState randomState,
                                       BiomeManager biomeManager, StructureManager structureManager,
                                       ChunkAccess chunk, CallbackInfo ci) {
        AquiferBarrierRecorder.endChunkGeneration();
    }
}
