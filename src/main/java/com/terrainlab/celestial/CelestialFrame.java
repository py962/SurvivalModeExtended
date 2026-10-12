package com.terrainlab.celestial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Replicates vanilla conduit frame activation for Celestial conduits: the 42 frame positions
 * of ConduitBlockEntity#updateShape (outer rings of the three axis planes at distance 2),
 * vanilla's 16/42 activation and full-power thresholds, and its stepped range formula.
 *
 * <p>Unlike vanilla, there is no water-submersion requirement; activation depends only on
 * the frame block count.
 */
public final class CelestialFrame {

    public static final int MIN_ACTIVE = 16;
    public static final int FULL_FRAME = 42;

    public static final List<Vec3i> FRAME_OFFSETS = buildOffsets();

    private CelestialFrame() {
    }

    private static List<Vec3i> buildOffsets() {
        List<Vec3i> offsets = new ArrayList<>(42);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int ax = Math.abs(dx);
                    int ay = Math.abs(dy);
                    int az = Math.abs(dz);
                    boolean valid = (dx == 0 && (ay == 2 || az == 2))
                            || (dy == 0 && (ax == 2 || az == 2))
                            || (dz == 0 && (ax == 2 || ay == 2));
                    if (valid) {
                        offsets.add(new Vec3i(dx, dy, dz));
                    }
                }
            }
        }
        return offsets;
    }

    /** Counts how many of the 42 frame positions around {@code pos} hold this variant's frame block. */
    public static int countFrameBlocks(Level level, BlockPos pos, CelestialVariant variant) {
        return matchingFramePositions(level, pos, variant).size();
    }

    /** Frame positions around {@code pos} that hold this variant's frame block; also used client-side
     * for the frame-to-conduit particle animation. */
    public static List<BlockPos> matchingFramePositions(Level level, BlockPos pos, CelestialVariant variant) {
        List<BlockPos> matches = new ArrayList<>();
        for (Vec3i offset : FRAME_OFFSETS) {
            BlockPos check = pos.offset(offset);
            if (variant.isFrameBlock(level.getBlockState(check))) {
                matches.add(check);
            }
        }
        return matches;
    }

    /** Vanilla's effect range: {@code count / 7 * 16} blocks, in steps of 16. Callers gate on MIN_ACTIVE. */
    public static int rangeFor(int frameBlockCount) {
        return frameBlockCount / 7 * 16;
    }

    public static boolean isFullPower(int frameBlockCount) {
        return frameBlockCount >= FULL_FRAME;
    }
}
