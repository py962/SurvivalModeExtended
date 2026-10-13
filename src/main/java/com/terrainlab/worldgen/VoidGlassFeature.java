package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import com.terrainlab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Replaces exposed End stone surfaces at Y 30 or below with void glass, with a chance that grows
 * with distance from the origin and is biased towards lower Y.
 */
public final class VoidGlassFeature implements Feature {

    private static final double MIN_DISTANCE = 1500.0;  // blocks; MIN_PROBABILITY from here
    private static final double MAX_DISTANCE = 5000.0;  // blocks; MAX_PROBABILITY from here

    private static final double MIN_PROBABILITY = 0.05;
    private static final double MAX_PROBABILITY = 0.95;

    // Y bias: lower surfaces are more likely.
    private static final int HIGH_Y = 50;   // minimum bias
    private static final int LOW_Y = 0;    // maximum bias

    public static final MapCodec<VoidGlassFeature> CODEC = MapCodec.unit(VoidGlassFeature::new);

    public VoidGlassFeature() {
    }

    @Override
    public MapCodec<VoidGlassFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {

        double distance = Math.sqrt(
                origin.getX() * (double) origin.getX()
                        + origin.getZ() * (double) origin.getZ());

        if (distance < MIN_DISTANCE) return false;

        double t = Math.min(1.0,
                (distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE));

        double baseProbability = MIN_PROBABILITY + t * (MAX_PROBABILITY - MIN_PROBABILITY);

        // Only the origin chunk's own columns, so each column is rolled once per chunk.
        ChunkPos chunkPos = ChunkPos.containing(origin);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();

        int placed = 0;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dy = 16; dy >= -16; dy--) {
                    BlockPos pos = new BlockPos(minX + dx, origin.getY() + dy, minZ + dz);
                    if (level.getBlockState(pos).is(Blocks.END_STONE)
                            && level.getBlockState(pos.above()).isAir()  && pos.getY() <= 30) {

                        int blockY = pos.getY();
                        double yFactor;
                        if (blockY <= LOW_Y) {
                            yFactor = 1.5;
                        } else if (blockY >= HIGH_Y) {
                            yFactor = 0.6;
                        } else {
                            double yT = (double)(blockY - LOW_Y) / (HIGH_Y - LOW_Y);
                            yFactor = 1.5 - (0.9 * yT); // 1.5 down to 0.6
                        }

                        double finalProbability = Math.min(0.98,
                                baseProbability * yFactor);

                        if (random.nextDouble() < finalProbability) {
                            level.setBlock(pos,
                                    ModBlocks.VOID_GLASS.get().defaultBlockState(),
                                    2);
                            placed++;
                        }
                        break;
                    }
                }
            }
        }
        return placed > 0;
    }
}
