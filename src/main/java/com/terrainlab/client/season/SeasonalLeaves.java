package com.terrainlab.client.season;

import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Client-only seasonal leaf appearance: which "look" a leaf block at a position shows right now.
 * <p>
 * Only oak, birch and cherry leaves in any Overworld biome except jungles are affected; everything
 * else keeps its vanilla look ({@link Look#VANILLA}). Nothing here touches world state: the look is chosen
 * when a chunk section is meshed, by {@link SeasonalLeavesModel} (texture swaps) and the tint sources in
 * {@link SeasonalFoliageClient} (colour), and {@link SeasonalLeafRerender} re-meshes sections as trees change.
 * <p>
 * Stagger: at a season boundary each tree keeps the previous season's look for a delay of 0..3 days, hashed
 * from the 5x5-block cell its leaves sit in, so a forest turns gradually rather than all at once.
 * <p>
 * Called from chunk-meshing worker threads, so the season clock is a volatile snapshot taken on the client
 * tick, and the biome check goes through a registered {@link ColorResolver} (the only biome access a
 * meshing {@link BlockAndTintGetter} offers), backed by vanilla's per-chunk tint cache.
 */
public final class SeasonalLeaves {

    private SeasonalLeaves() {
    }

    /** Leaves of one tree mostly share a cell of this many blocks on X and Z. */
    public static final int CELL_SIZE = 5;

    /** Trees change look at a hashed moment within this many ticks after a season boundary (3 days). */
    public static final int STAGGER_TICKS = 72_000;

    public enum Tree { OAK, BIRCH, CHERRY }

    public enum Look {
        /** Vanilla model and colour. */
        VANILLA,
        /** Oak: vanilla model, russet tint (one of {@link #SHADE_COUNT} shades per block). */
        BROWN,
        /** Birch: pale, frost-speckled texture. */
        FROSTY,
        /** Birch: vanilla birch green with yellow flower clusters. */
        FLOWERING,
        /** Birch: golden texture (one of {@link #SHADE_COUNT} shades per block: gold, lemon, amber). */
        YELLOW,
        /** Cherry: crimson texture. */
        CRIMSON
    }

    // Per season, in Season ordinal order: MIDWINTER, EARLY_SPRING, LATE_SPRING, MIDSUMMER, EARLY_AUTUMN, LATE_AUTUMN.
    private static final Look[] OAK_LOOKS = {
            Look.BROWN, Look.VANILLA, Look.VANILLA, Look.VANILLA, Look.BROWN, Look.BROWN};
    private static final Look[] BIRCH_LOOKS = {
            Look.FROSTY, Look.FLOWERING, Look.FLOWERING, Look.VANILLA, Look.YELLOW, Look.YELLOW};
    private static final Look[] CHERRY_LOOKS = {
            Look.VANILLA, Look.VANILLA, Look.VANILLA, Look.CRIMSON, Look.CRIMSON, Look.CRIMSON};

    /** Autumn looks come in this many shades, picked per leaf block from its position. */
    public static final int SHADE_COUNT = 3;

    /**
     * The autumn shade of the leaf block at {@code pos}, in [0, SHADE_COUNT): 0 is the base colour, 1 the
     * lighter one, 2 the deeper one. A stable hash of the position, so re-meshing never changes it.
     */
    public static int shadeAt(BlockPos pos) {
        long h = pos.getX() * 0x9E3779B97F4A7C15L ^ pos.getY() * 0xD6E8FEB86659FD93L ^ pos.getZ() * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (int) Long.remainderUnsigned(h, SHADE_COUNT);
    }

    /** Affected-biome flag per Biome instance; cleared with the client session (biomes are per connection). */
    private static final Map<Biome, Boolean> BIOME_FLAGS = new ConcurrentHashMap<>();

    private static final int MASK_ON = ARGB.color(0, 255, 0);
    private static final int MASK_OFF = ARGB.color(255, 0, 0);

    /**
     * Encodes "is this a seasonal-leaves biome" as a colour so vanilla's biome blending and tint cache apply:
     * green for yes, red for no. Read with {@link #isSeasonalBiome}.
     */
    public static final ColorResolver BIOME_MASK = (biome, x, z) ->
            BIOME_FLAGS.computeIfAbsent(biome, SeasonalLeaves::computeBiomeFlag) ? MASK_ON : MASK_OFF;

    private static volatile boolean clockValid;
    private static volatile long yearTick;

    private static boolean computeBiomeFlag(Biome biome) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        // Every Overworld biome (vanilla, this mod's and other mods'): anything not tagged Nether or End.
        // Jungles are left out: they have no autumn, and their bushes are made of oak leaves.
        net.minecraft.core.Holder<Biome> holder = level.registryAccess().lookupOrThrow(Registries.BIOME).wrapAsHolder(biome);
        return !holder.is(net.minecraft.tags.BiomeTags.IS_NETHER) && !holder.is(net.minecraft.tags.BiomeTags.IS_END)
                && !holder.is(net.minecraft.tags.BiomeTags.IS_JUNGLE);
    }

    /** Main thread, every client tick. */
    static void updateClock(@Nullable ClientLevel level) {
        if (level == null) {
            clockValid = false;
            return;
        }
        yearTick = SeasonManager.yearRelativeTick(level.getGameTime(), ClientSeasonState.getSeasonalOffset());
        clockValid = true;
    }

    static void resetSession() {
        clockValid = false;
        BIOME_FLAGS.clear();
    }

    static boolean clockValid() {
        return clockValid;
    }

    static long yearTick() {
        return yearTick;
    }

    public static @Nullable Tree treeOf(Block block) {
        if (block == Blocks.OAK_LEAVES) return Tree.OAK;
        if (block == Blocks.BIRCH_LEAVES) return Tree.BIRCH;
        if (block == Blocks.CHERRY_LEAVES) return Tree.CHERRY;
        return null;
    }

    /** The look a tree of this type shows at {@code pos} right now. Thread-safe. */
    public static Look lookAt(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, Tree tree) {
        if (!clockValid || level == null || pos == null || !isSeasonalBiome(level, pos)) {
            return Look.VANILLA;
        }
        return looksFor(tree)[effectiveSeason(yearTick, pos.getX(), pos.getZ()).ordinal()];
    }

    public static boolean isSeasonalBiome(BlockAndTintGetter level, BlockPos pos) {
        int mask = level.getBlockTint(pos, BIOME_MASK);
        // -1 (white) is what non-world getters return: treat as "not seasonal".
        return ARGB.green(mask) > ARGB.red(mask);
    }

    private static Look[] looksFor(Tree tree) {
        return switch (tree) {
            case OAK -> OAK_LOOKS;
            case BIRCH -> BIRCH_LOOKS;
            case CHERRY -> CHERRY_LOOKS;
        };
    }

    /** The calendar state a tree at (x, z) currently shows: the previous one until its stagger delay passes. */
    static Season effectiveSeason(long yearTick, int x, int z) {
        Season season = Season.fromYearTick(yearTick);
        long into = Season.ticksIntoCurrentState(yearTick);
        if (into < cellDelay(Math.floorDiv(x, CELL_SIZE), Math.floorDiv(z, CELL_SIZE))) {
            return previous(season);
        }
        return season;
    }

    static Season previous(Season season) {
        return Season.values()[(season.ordinal() + Season.STATE_COUNT - 1) % Season.STATE_COUNT];
    }

    /** Ticks after a season boundary at which the trees in this cell switch: 0..STAGGER_TICKS-1. */
    static int cellDelay(int cellX, int cellZ) {
        long h = cellX * 0x9E3779B97F4A7C15L ^ cellZ * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (int) Long.remainderUnsigned(h, STAGGER_TICKS);
    }
}
