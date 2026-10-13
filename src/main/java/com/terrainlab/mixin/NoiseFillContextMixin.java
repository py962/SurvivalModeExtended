package com.terrainlab.mixin;

import com.terrainlab.worldgen.AquiferBarrierRecorder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scopes AquiferBarrierRecorder to real chunk generation (noise fill and carving), so aquifers built by
 * iterateNoiseColumn for height probes are not recorded.
 * <p>26.x: noise fill, surface and carving now all run inside one buildTerrain task on the same thread, and
 * doFill no longer receives the StructureManager. The scope therefore opens when buildTerrain creates its
 * NoiseChunk (createNoiseChunk is only called from buildTerrain) and closes when generateCarvers returns.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseFillContextMixin {

    @Inject(method = "createNoiseChunk", at = @At("HEAD"), require = 1)
    private void terrainlab$beginFill(ChunkAccess chunk, StructureManager structureManager, Blender blender,
                                      RandomState randomState, NoiseSettings noiseSettings,
                                      CallbackInfoReturnable<NoiseChunk> cir) {
        LevelAccessor level = ((StructureManagerAccessor) structureManager).terrainlab$getLevel();
        if (level instanceof ServerLevelAccessor serverLevelAccessor) {
            AquiferBarrierRecorder.beginChunkGeneration(serverLevelAccessor.getLevel().dimension());
        } else {
            AquiferBarrierRecorder.endChunkGeneration();
        }
    }

    @Inject(method = "generateCarvers", at = @At("RETURN"), require = 1)
    private void terrainlab$endCarvers(ChunkAccess chunk, Blender blender, NoiseChunk noiseChunk,
                                       RandomState randomState, BiomeManager biomeManager,
                                       @Nullable WorldGenRegion carverBiomeRegion, MaterialRule materialRule,
                                       CallbackInfo ci) {
        AquiferBarrierRecorder.endChunkGeneration();
    }
}
