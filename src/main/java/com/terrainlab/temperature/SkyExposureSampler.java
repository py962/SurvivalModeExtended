package com.terrainlab.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.Identifier;

/**
 * Graduated sky exposure fraction: a weighted 3x3 sample of columns around a position (centre 4,
 * orthogonal 2, diagonal 1). Submerged sample points count as unexposed, and glass at a sample point
 * counts as exposed. A submerged centre is handled by LocalTemperatureService.
 * TODO: cache results per position and invalidate on block changes; this rescans on every call.
 */
public final class SkyExposureSampler {

    private static final int[][] OFFSETS_WITH_WEIGHTS = {
            {0, 0, 4},   // centre
            {1, 0, 2}, {-1, 0, 2}, {0, 1, 2}, {0, -1, 2}, // orthogonal
            {1, 1, 1}, {1, -1, 1}, {-1, 1, 1}, {-1, -1, 1} // diagonal
    };

    private static final int TOTAL_WEIGHT = 16;

    /** Vanilla glass block tag. */
    private static final TagKey<Block> GLASS_TAG = BlockTags.create(
            Identifier.parse("minecraft:glass")
    );

    private SkyExposureSampler() {
    }

    /** Exposure in [0, 1]: 1 when every sample sees the sky, 0 when all are enclosed or submerged. */
    public static double getExposure(LevelReader level, BlockPos pos) {
        int weightedSum = 0;

        for (int[] offset : OFFSETS_WITH_WEIGHTS) {
            BlockPos samplePos = pos.offset(offset[0], 0, offset[1]);

            boolean submerged = !level.getFluidState(samplePos).isEmpty();

            if (!submerged && (level.canSeeSky(samplePos) || isGlass(level, samplePos))) {
                weightedSum += offset[2];
            }
        }

        return weightedSum / (double) TOTAL_WEIGHT;
    }

    private static boolean isGlass(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(GLASS_TAG);
    }
}
