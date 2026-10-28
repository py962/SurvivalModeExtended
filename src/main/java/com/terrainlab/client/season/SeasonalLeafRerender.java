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
 * section is re-meshed. Every leaf block switches at its own moment ({@link SeasonalLeaves#blockDelay}),
 * so during the 3-day stagger window after a season boundary some leaves in nearly every column change
 * between checks: every {@link #INTERVAL_TICKS} the loaded seasonal-biome columns are re-meshed, and
 * outside the window nothing is. Large clock jumps (seasonal offset synced or changed, tick sprinting)
 * re-mesh every loaded seasonal column once.
 */
final class SeasonalLeafRerender {

    private SeasonalLeafRerender() {
    }

    private static final int INTERVAL_TICKS = 400;
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
            refresh(minecraft, level, player);
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
            refresh(minecraft, level, player);
        } else if (elapsed > 0) {
            boolean sameState = Season.fromYearTick(now) == Season.fromYearTick(lastYearTick);
            long tLast = sameState ? Season.ticksIntoCurrentState(lastYearTick) : -1L;
            if (tLast < SeasonalLeaves.STAGGER_TICKS - 1) {
                refresh(minecraft, level, player);
            }
        }
        lastYearTick = now;
    }

    /**
     * Marks every loaded seasonal-biome chunk column around the player dirty.
     */
    private static void refresh(Minecraft minecraft, ClientLevel level, Player player) {
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
                if (!columnTouchesSeasonalBiome(level, cx, cz, probe)) {
                    continue;
                }
                minecraft.levelExtractor.setSectionRangeDirty(cx, minSection, cz, cx, maxSection, cz);
            }
        }
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
