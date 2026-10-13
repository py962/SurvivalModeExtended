package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;

/**
 * Large mountain spring: carves a 4-wide, 2-tall, 2-deep pocket into a solid wall and fills it with
 * water sources, leaving an open mouth in front so the water pours out and flows downhill.
 */
public final class LargeSpringFeature implements Feature {

    private static final int WIDTH = 4;
    private static final int HEIGHT = 2;
    private static final int EMBED_DEPTH = 2;

    public static final MapCodec<LargeSpringFeature> CODEC = MapCodec.unit(LargeSpringFeature::new);

    public LargeSpringFeature() {
    }

    @Override
    public MapCodec<LargeSpringFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {

        // Shuffle the horizontal directions so springs don't all face one way.
        Direction[] directions = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
        for (int i = directions.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Direction temp = directions[i];
            directions[i] = directions[j];
            directions[j] = temp;
        }

        for (Direction outward : directions) {
            if (tryPlaceAgainstWall(level, origin, outward)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryPlaceAgainstWall(WorldGenLevel level, BlockPos origin, Direction outward) {
        Direction into = outward.getOpposite();
        Direction perpendicular = outward.getClockWise();

        // Validate every cell first: the mouth must be replaceable, the carved layers and the layer
        // behind them solid.
        for (int w = 0; w < WIDTH; w++) {
            int perpOffset = w - (WIDTH / 2);
            BlockPos columnFront = origin.relative(perpendicular, perpOffset);

            for (int h = 0; h < HEIGHT; h++) {
                BlockPos mouth = columnFront.above(h);
                if (!level.getBlockState(mouth).canBeReplaced()) {
                    return false;
                }

                for (int d = 1; d <= EMBED_DEPTH; d++) {
                    BlockPos carved = mouth.relative(into, d);
                    if (!level.getBlockState(carved).isSolid()) {
                        return false;
                    }
                }

                BlockPos backing = mouth.relative(into, EMBED_DEPTH + 1);
                if (!level.getBlockState(backing).isSolid()) {
                    return false;
                }
            }

            // Solid floor under the mouth and every carved layer.
            if (!level.getBlockState(columnFront.below()).isSolid()) {
                return false;
            }
            for (int d = 1; d <= EMBED_DEPTH; d++) {
                BlockPos carvedBase = columnFront.relative(into, d);
                if (!level.getBlockState(carvedBase.below()).isSolid()) {
                    return false;
                }
            }
        }

        // Fill the pocket. setBlock alone does not start flow, so schedule a fluid tick per cell,
        // as vanilla SpringFeature does.
        for (int w = 0; w < WIDTH; w++) {
            int perpOffset = w - (WIDTH / 2);
            BlockPos columnFront = origin.relative(perpendicular, perpOffset);
            for (int h = 0; h < HEIGHT; h++) {
                BlockPos mouth = columnFront.above(h);
                for (int d = 1; d <= EMBED_DEPTH; d++) {
                    BlockPos cell = mouth.relative(into, d);
                    level.setBlock(cell, Blocks.WATER.defaultBlockState(), 2);
                    level.scheduleTick(cell, Fluids.WATER, 0);
                }
            }
        }

        return true;
    }
}
