
package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.List;

/**
 * Fills open air below the configured water level in swamp and marsh biome
 * columns, continuing through underground biome boundaries until solid ground
 * is reached.
 */
public final class SwampFloodFeature implements Feature {

    private static final int SEA_LEVEL = 63;

    private static final List<ResourceKey<Biome>> TARGET_BIOMES = List.of(
            Biomes.SWAMP,
            Biomes.MANGROVE_SWAMP,
            ResourceKey.create(
                    Registries.BIOME,
                    Identifier.fromNamespaceAndPath("terrainlab", "marsh")
            ),
            ResourceKey.create(
                    Registries.BIOME,
                    Identifier.fromNamespaceAndPath("terrainlab", "frozen_marsh")
            )
    );

    public static final MapCodec<SwampFloodFeature> CODEC = MapCodec.unit(SwampFloodFeature::new);

    public SwampFloodFeature() {
    }

    @Override
    public MapCodec<SwampFloodFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ChunkPos chunkPos = ChunkPos.containing(origin);

        boolean placedAny = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int worldX = chunkPos.getMinBlockX() + dx;
                int worldZ = chunkPos.getMinBlockZ() + dz;

                // Determine whether this column belongs to a target biome
                // at the configured water level.
                pos.set(worldX, SEA_LEVEL - 1, worldZ);

                Holder<Biome> surfaceBiome = level.getBiome(pos);
                if (!isTargetBiome(surfaceBiome)) {
                    continue;
                }

                // Fill downward without checking underground biome boundaries.
                for (int y = SEA_LEVEL - 1; y >= level.getMinY(); y--) {
                    pos.set(worldX, y, worldZ);

                    BlockState state = level.getBlockState(pos);

                    if (state.isAir()) {
                        level.setBlock(
                                pos,
                                Blocks.WATER.defaultBlockState(),
                                2
                        );
                        placedAny = true;
                    } else if (!state.is(Blocks.WATER)) {
                        // Stop at the first solid or other non-water block.
                        break;
                    }
                }
            }
        }

        return placedAny;
    }

    private boolean isTargetBiome(Holder<Biome> biome) {
        for (ResourceKey<Biome> key : TARGET_BIOMES) {
            if (biome.is(key)) {
                return true;
            }
        }
        return false;
    }
}
