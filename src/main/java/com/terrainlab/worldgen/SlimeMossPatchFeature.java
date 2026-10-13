package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;

/** Small disk of slime blocks capped with moss blocks, replacing stone, clay, dirt or mud. */
public final class SlimeMossPatchFeature implements Feature {

    // Disk of radius 1-2 one block below the origin: target blocks become slime, with moss on top where open.
    private static final int MIN_RADIUS = 1;
    private static final int MAX_RADIUS = 2;

    public static final MapCodec<SlimeMossPatchFeature> CODEC = MapCodec.unit(SlimeMossPatchFeature::new);

    public SlimeMossPatchFeature() {
    }

    @Override
    public MapCodec<SlimeMossPatchFeature> codec() {
        return CODEC;
    }

    private static boolean isTargetBlock(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.STONE)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.MUD);
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {

        int radius = MIN_RADIUS + random.nextInt(MAX_RADIUS - MIN_RADIUS + 1);
        boolean placedAny = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;

                pos.set(origin.getX() + dx, origin.getY() - 1, origin.getZ() + dz);
                var state = level.getBlockState(pos);
                if (!isTargetBlock(state)) continue;

                level.setBlock(pos, Blocks.SLIME_BLOCK.defaultBlockState(), 2);
                placedAny = true;

                BlockPos above = pos.above();
                if (level.getBlockState(above).isAir()) {
                    level.setBlock(above, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                }
            }
        }

        return placedAny;
    }
}
