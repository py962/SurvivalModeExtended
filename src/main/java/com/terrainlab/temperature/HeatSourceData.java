package com.terrainlab.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One indexed heat source: position, strength, and how enclosed its surroundings are (which sets how fast
 * its heat falls off). The enclosure factor starts as {@link Double#NaN} and is computed lazily by
 * HeatContributionService the first time something is in range.
 */
public record HeatSourceData(
        BlockPos sourcePos,
        double baseStrength,
        double enclosureFactor // 1 - (reachable / totalCellsInRadius); NaN = not computed yet
) {

    /** A freshly indexed source whose enclosure hasn't been worked out yet. */
    public static HeatSourceData pending(BlockPos sourcePos, double baseStrength) {
        return new HeatSourceData(sourcePos.immutable(), baseStrength, Double.NaN);
    }

    public boolean hasEnclosure() {
        return !Double.isNaN(enclosureFactor);
    }

    public HeatSourceData withEnclosure(double enclosure) {
        return new HeatSourceData(sourcePos, baseStrength, enclosure);
    }

    /** Same data with the enclosure cleared, so it's recomputed on next use. */
    public HeatSourceData withoutEnclosure() {
        return new HeatSourceData(sourcePos, baseStrength, Double.NaN);
    }

    /**
     * Degrees C this source adds to the ambient temperature at {@code pos}: 0 beyond
     * {@link HeatFloodFill#RADIUS} or behind a full block; otherwise full strength up to 1 block away, then a
     * linear decay whose rate scales from {@code DECAY_RATE_AT_OPEN} (open air) to
     * {@code DECAY_RATE_AT_FULLY_ENCLOSED} with the enclosure factor. Partial blocks do not obstruct.
     */
    public double contributionAt(LevelReader level, BlockPos pos) {
        double distance = Math.sqrt(sourcePos.distSqr(pos));
        if (distance > HeatFloodFill.RADIUS) return 0.0;

        if (blockedByFullBlock(level, sourcePos, pos)) return 0.0;

        double decayDistance = Math.max(0.0, distance - 1.0);

        // Missing enclosure falls back to the open-air rate rather than propagating NaN.
        double enclosure = hasEnclosure() ? enclosureFactor : 0.0;
        double decayRate = HeatFloodFill.DECAY_RATE_AT_OPEN
                + (HeatFloodFill.DECAY_RATE_AT_FULLY_ENCLOSED - HeatFloodFill.DECAY_RATE_AT_OPEN) * enclosure;

        double decayed = baseStrength - decayRate * decayDistance;
        return Math.max(0.0, decayed);
    }

    /**
     * Coarse line march (~0.5-block steps) between block centres; true if a full collision block lies
     * between source and {@code pos}. Samples inside the source or target block are skipped.
     */
    private static boolean blockedByFullBlock(LevelReader level, BlockPos source, BlockPos pos) {
        Vec3 from = Vec3.atCenterOf(source);
        Vec3 to = Vec3.atCenterOf(pos);
        double dist = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(dist * 2.0)); // ~0.5-block sampling
        BlockPos lastPos = null;
        for (int i = 1; i < steps; i++) {
            Vec3 point = from.lerp(to, (double) i / steps);
            BlockPos sample = BlockPos.containing(point);
            if (sample.equals(lastPos)) continue;
            lastPos = sample;
            if (sample.equals(source) || sample.equals(pos)) continue;

            BlockState state = level.getBlockState(sample);
            if (state.isCollisionShapeFullBlock(level, sample)) {
                return true;
            }
        }
        return false;
    }
}
