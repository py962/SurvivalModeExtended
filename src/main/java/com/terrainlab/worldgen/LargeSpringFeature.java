package com.terrainlab.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Large mountain spring: carves a 4-wide, 2-tall, 2-deep pocket into a solid wall and fills it with
 * water sources, leaving an open mouth in front so the water pours out and flows downhill.
 */
public class LargeSpringFeature extends Feature<NoneFeatureConfiguration> {

    private static final int WIDTH = 4;
    private static final int HEIGHT = 2;
    private static final int EMBED_DEPTH = 2;

    public LargeSpringFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

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
                    if (!level.getBlockState(carved).blocksMotion()) {
                        return false;
                    }
                }

                BlockPos backing = mouth.relative(into, EMBED_DEPTH + 1);
                if (!level.getBlockState(backing).blocksMotion()) {
                    return false;
                }
            }

            // Solid floor under the mouth and every carved layer.
            if (!level.getBlockState(columnFront.below()).blocksMotion()) {
                return false;
            }
            for (int d = 1; d <= EMBED_DEPTH; d++) {
                BlockPos carvedBase = columnFront.relative(into, d);
                if (!level.getBlockState(carvedBase.below()).blocksMotion()) {
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
