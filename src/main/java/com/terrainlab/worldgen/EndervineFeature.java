package com.terrainlab.worldgen;

import com.mojang.serialization.Codec;
import com.terrainlab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Places endervine chains hanging from End stone ceilings and growing out of End stone walls. */
public class EndervineFeature extends Feature<NoneFeatureConfiguration> {

    public EndervineFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        var random = context.random();

        int placed = 0;

        for (int attempt = 0; attempt < 3; attempt++) {
            int dx = random.nextInt(9) - 4;
            int dy = random.nextInt(5) - 2;
            int dz = random.nextInt(9) - 4;
            BlockPos pos = origin.offset(dx, dy, dz);

            // Hang down from an End stone ceiling
            if (level.getBlockState(pos).is(Blocks.END_STONE)
                    && level.getBlockState(pos.below()).isAir()) {
                placeVineChain(level, pos.below(), random, 8 + random.nextInt(8));
                placed++;
            }

            // Or grow out from an End stone wall
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos side = pos.relative(dir);
                if (level.getBlockState(pos).is(Blocks.END_STONE)
                        && level.getBlockState(side).isAir()) {
                    placeVineChain(level, side, random, 6 + random.nextInt(6));
                    placed++;
                    break;
                }
            }
        }

        return placed > 0;
    }

    private void placeVineChain(WorldGenLevel level, BlockPos start,
                                net.minecraft.util.RandomSource random, int length) {
        BlockPos current = start;

        for (int i = 0; i < length; i++) {
            if (!level.getBlockState(current).isAir()) break;

            // Only the last block is the growing head.
            if (i == length - 1) {
                level.setBlock(current,
                        ModBlocks.ENDERVINE_HEAD.get().defaultBlockState(), 2);
            } else {
                level.setBlock(current,
                        ModBlocks.ENDERVINE_BODY.get().defaultBlockState(), 2);
            }

            // Mostly downward, one step sideways a third of the time
            if (random.nextInt(3) == 0) {
                Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                current = current.relative(dir);
            } else {
                current = current.below();
            }
        }
    }
}
