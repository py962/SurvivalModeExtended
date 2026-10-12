package com.terrainlab.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Places a flat obsidian platform covering the chunks within PLATFORM_CHUNK_RADIUS of chunk (0, 0). */
public class ObsidianIslandFeature extends Feature<NoneFeatureConfiguration> {

    // Radius in chunks: 2 gives a 5x5-chunk (80x80 block) platform.
    private static final int PLATFORM_CHUNK_RADIUS = 2;
    private static final int PLATFORM_Y = 30;
    private static final int THICKNESS = 3;

    public ObsidianIslandFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();

        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;

        if (Math.abs(chunkX) > PLATFORM_CHUNK_RADIUS ||
                Math.abs(chunkZ) > PLATFORM_CHUNK_RADIUS) {
            return false;
        }

        int startX
 = chunkX * 16;
        int startZ = chunkZ * 16;

        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = PLATFORM_Y - THICKNESS + 1; y <= PLATFORM_Y; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    level.setBlock(pos, Blocks.OBSIDIAN.defaultBlockState(), 2);
                }
            }
        }

        return true;
    }
}
