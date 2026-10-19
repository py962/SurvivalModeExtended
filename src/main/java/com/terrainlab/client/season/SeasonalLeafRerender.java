package com.terrainlab.client.season;

import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.season.Season;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Leaf tints and texture variants are baked into chunk meshes, so a tree only changes on screen when its
 * section is re-meshed. Every {@link #INTERVAL_TICKS} this finds the stagger cells whose switch moment
 * ({@link SeasonalLeaves#cellDelay}) fell inside the elapsed window and marks just those chunk columns
 * dirty (and only when they lie in a seasonal biome). Inside a 3-day stagger window that is a few
 * percent of loaded columns per check; outside it, nothing. Large clock jumps (seasonal offset synced or
 * changed, tick sprinting) re-mesh every loaded seasonal column once.
 */
final class SeasonalLeafRerender {

    private SeasonalLeafRerender() {
    }

    private static final int INTERVAL_TICKS = 100;
    /** Elapsed year ticks beyond which per-cell tracking is abandoned in favour of a full refresh. */
    private static final long JUMP_TICKS = 6_000L;

    private static long lastYearTick = -1L;
    private static long lastOffset;
    private static int counter;

    static void reset() {
        lastYearTick = -1L;
        counter = 0;
    }

    static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || !SeasonalLeaves.clockValid()) {
            reset();
            return;
        }
        long now = SeasonalLeaves.yearTick();
        long offset = ClientSeasonState.getSeasonalOffset();
        if (lastYearTick < 0) {
            // Fresh world: sections meshed from now on read the live clock.
            lastYearTick = now;
            lastOffset = offset;
            return;
        }
        if (offset != lastOffset) {
            // Offset arrived (login) or changed: everything meshed so far may show the wrong season.
            refresh(minecraft, level, player, -1L, -1L);
            lastYearTick = now;
            lastOffset = offset;
            return;
        }
        if (++counter < INTERVAL_TICKS) {
            return;
        }
        counter = 0;

        long elapsed = Math.floorMod(now - lastYearTick, Season.YEAR_LENGTH_TICKS);
        if (elapsed > JUMP_TICKS) {
            refresh(minecraft, level, player, -1L, -1L);
        } else if (elapsed > 0) {
            long tNow = Season.ticksIntoCurrentState(now);
            boolean sameState = Season.fromYearTick(now) == Season.fromYearTick(lastYearTick);
            long tLast = sameState ? Season.ticksIntoCurrentState(lastYearTick) : -1L;
            if (tLast < SeasonalLeaves.STAGGER_TICKS - 1) {
                refresh(minecraft, level, player, tLast, tNow);
            }
        }
        lastYearTick = now;
    }

    /**
     * Marks loaded seasonal-biome chunk columns around the player dirty. With a window (tLast, tNow] only the
     * columns containing a cell that switched inside it; with tLast = tNow = -1, all of them.
     */
    private static void refresh(Minecraft minecraft, ClientLevel level, Player player, long tLast, long tNow) {
        boolean all = tLast < 0 && tNow < 0;
        int radius = minecraft.options.getEffectiveRenderDistance() + 1;
        int pcx = SectionPos.blockToSectionCoord(player.getBlockX());
        int pcz = SectionPos.blockToSectionCoord(player.getBlockZ());
        int minSection = level.getMinSectionY();
        int maxSection = level.getMaxSectionY();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                if (!all && !anyCellSwitched(cx, cz, tLast, tNow)) {
                    continue;
                }
                if (!columnTouchesSeasonalBiome(level, cx, cz, probe)) {
                    continue;
                }
                minecraft.levelExtractor.setSectionRangeDirty(cx, minSection, cz, cx, maxSection, cz);
            }
        }
    }

    private static boolean anyCellSwitched(int cx, int cz, long tLast, long tNow) {
        int x0 = Math.floorDiv(cx * 16, SeasonalLeaves.CELL_SIZE);
        int x1 = Math.floorDiv(cx * 16 + 15, SeasonalLeaves.CELL_SIZE);
        int z0 = Math.floorDiv(cz * 16, SeasonalLeaves.CELL_SIZE);
        int z1 = Math.floorDiv(cz * 16 + 15, SeasonalLeaves.CELL_SIZE);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int delay = SeasonalLeaves.cellDelay(x, z);
                if (delay > tLast && delay <= tNow) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Samples the column's centre and corners at surface height. */
    private static boolean columnTouchesSeasonalBiome(ClientLevel level, int cx, int cz, BlockPos.MutableBlockPos probe) {
        int bx = cx * 16;
        int bz = cz * 16;
        int[][] points = {{8, 8}, {0, 0}, {15, 0}, {0, 15}, {15, 15}};
        for (int[] p : points) {
            int x = bx + p[0];
            int z = bz + p[1];
            probe.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
            if (SeasonalLeaves.isSeasonalBiome(level, probe)) {
                return true;
            }
        }
        return false;
    }
}
