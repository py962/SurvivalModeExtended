package com.terrainlab.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bounded flood fill from a heat source through air (and snow layers) within a
 * Chebyshev RADIUS cube; the unreached fraction of the cube gives the source's
 * enclosure factor (0 = open, 1 = sealed).
 */
public final class HeatFloodFill {

    public static final int RADIUS = 8;

    // Heat decay in degrees C per block, linear in enclosure factor between these
    // endpoints. Absolute values: revisit if CAMPFIRE_STRENGTH/LAVA_STRENGTH change.
    public static final double DECAY_RATE_AT_OPEN = 15.0;
    public static final double DECAY_RATE_AT_FULLY_ENCLOSED = 5.0;

    private HeatFloodFill() {
    }

    private static final int SIDE = 2 * RADIUS + 1;
    private static final int TOTAL_CELLS = SIDE * SIDE * SIDE;

    /** Result of an enclosure flood fill. */
    public record Enclosure(double factor, boolean complete) {
    }

    /**
     * Builds a source with its enclosure computed immediately. Used only by the
     * unregistered ModHeatSourceHandler; live code uses HeatSourceData#pending.
     */
    public static HeatSourceData compute(LevelReader level, BlockPos sourcePos, double strength) {
        return new HeatSourceData(sourcePos.immutable(), strength, computeEnclosure(level, sourcePos).factor());
    }

    /**
     * Computes the enclosure factor: 1 - reached cells / cube size. Never loads a
     * chunk: cells in unloaded chunks count as walls and the result is marked
     * incomplete so callers do not cache it.
     */
    public static Enclosure computeEnclosure(LevelReader level, BlockPos sourcePos) {
        int sx = sourcePos.getX();
        int sy = sourcePos.getY();
        int sz = sourcePos.getZ();

        // The cube spans at most 2 chunks per axis; check each once.
        int minCX = SectionPos.blockToSectionCoord(sx - RADIUS);
        int minCZ = SectionPos.blockToSectionCoord(sz - RADIUS);
        int spanX = SectionPos.blockToSectionCoord(sx + RADIUS) - minCX + 1;
        int spanZ = SectionPos.blockToSectionCoord(sz + RADIUS) - minCZ + 1;
        boolean[] chunkLoaded = new boolean[spanX * spanZ];
        boolean allLoaded = true;
        for (int i = 0; i < spanX; i++) {
            for (int j = 0; j < spanZ; j++) {
                boolean loaded = HeatChunks.isLoaded(level, minCX + i, minCZ + j);
                chunkLoaded[i * spanZ + j] = loaded;
                allLoaded &= loaded;
            }
        }

        boolean[] visited = new boolean[TOTAL_CELLS];
        int[] queue = new int[TOTAL_CELLS];
        int head = 0;
        int tail = 0;
        int start = index(RADIUS, RADIUS, RADIUS);
        visited[start] = true;
        queue[tail++] = start;
        int reached = 1; // the source's own cell
        boolean complete = true;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        while (head < tail) {
            int cell = queue[head++];
            int cx = cell / (SIDE * SIDE);
            int cy = (cell / SIDE) % SIDE;
            int cz = cell % SIDE;
            for (int d = 0; d < 6; d++) {
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                int nz = cz + DZ[d];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= SIDE || ny >= SIDE || nz >= SIDE) {
                    continue;
                }
                int next = index(nx, ny, nz);
                if (visited[next]) {
                    continue;
                }
                visited[next] = true;
                int wx = sx + nx - RADIUS;
                int wy = sy + ny - RADIUS;
                int wz = sz + nz - RADIUS;
                if (!allLoaded) {
                    int ci = SectionPos.blockToSectionCoord(wx) - minCX;
                    int cj = SectionPos.blockToSectionCoord(wz) - minCZ;
                    if (!chunkLoaded[ci * spanZ + cj]) {
                        complete = false; // unknown - treated as a wall
                        continue;
                    }
                }
                if (!isPassableForHeatFlow(level.getBlockState(cursor.set(wx, wy, wz)))) {
                    continue;
                }
                reached++;
                queue[tail++] = next;
            }
        }

        double enclosure = 1.0 - (reached / (double) TOTAL_CELLS);
        return new Enclosure(enclosure, complete);
    }

    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 0, 0, 1, -1};

    private static int index(int x, int y, int z) {
        return (x * SIDE + y) * SIDE + z;
    }

    // Snow layers do not seal a space, so they pass heat like air (SNOW_BLOCK does not).
    private static boolean isPassableForHeatFlow(BlockState state) {
        return state.isAir() || state.is(Blocks.SNOW);
    }

    private static int chebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()),
                Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ())));
    }
}
