package com.terrainlab.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the nearest standable position (solid floor, two air blocks, no fluid or hazard) near an origin;
 * shared by pet teleport, goat-horn summoning and patrol spawning.
 */
public class SafeSpotFinder {

    /** Nearest safe position within {@code radius} horizontally and 3 blocks vertically, or null. */
    public static BlockPos findNearestSafeSpot(Level level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    if (isSafe(level, candidate)) {
                        double distSq = origin.distSqr(candidate);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    public static boolean isSafe(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        BlockState at = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());

        if (!below.isSolid()) return false;
        if (!at.getFluidState().isEmpty() || !above.getFluidState().isEmpty()) return false;
        if (at.is(Blocks.FIRE)) return false;
        if (at.is(Blocks.LAVA)) return false;
        if (at.is(Blocks.POWDER_SNOW)) return false;

        return at.isAir() && above.isAir();
    }
}
