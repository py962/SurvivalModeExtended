package com.terrainlab.season.plants;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Seasonal presence of naturally generated ground plants (leaf litter, flowers, pumpkins, mushrooms) in
 * the biomes tagged {@code #terrainlab:has_seasonal_ground_plants}. Overworld only, server only.
 * <p>
 * Out of season, a natural plant is removed (no drops, no particles) and recorded as dormant in its
 * chunk's {@link ModAttachments#SEASONAL_PLANTS} attachment; when its season returns it is put back if
 * the spot is free and it can survive there, otherwise the record is dropped. Plants placed by an entity
 * or grown by bonemeal are recorded as player-placed and never touched.
 * <p>
 * Each position flips {@link #delayTicks} (a hash in [0, STAGGER_TICKS)) after the calendar boundary,
 * so a position's effective season is the season at {@code yearTick - delay}. That makes every scan
 * stateless: it just compares each plant (or dormant record) with its own effective season.
 * <p>
 * Scanning: loaded chunks only, a fixed budget per tick. Chunks are queued on load (catch-up and newly
 * generated chunks), swept repeatedly while a stagger window that changes something is open, once more
 * when it closes, and in full after a time jump. Only the surface layer (top motion-blocking non-leaf
 * block, which catches pumpkins) and the block above it (flowers, litter, mushrooms, tall-flower lower
 * halves) are read per column; plants under overhangs or roofs are ignored.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class SeasonalPlantService {

    private SeasonalPlantService() {
    }

    /** Spread of the per-position flip delay after a boundary: 3 in-game days. */
    public static final long STAGGER_TICKS = 72_000L;

    /** Chunks scanned per tick (load queue first, then the sweep queue). */
    private static final int CHUNKS_PER_TICK = 6;

    /** Minimum ticks between full re-sweeps during an open stagger window. */
    private static final long MIN_SWEEP_INTERVAL = 600L;

    /** A game-time step larger than this (or backwards) is treated as a time jump. */
    private static final long JUMP_THRESHOLD = 1_200L;

    /** Removal: no drops; neighbours are updated so nothing is left floating. */
    private static final int REMOVE_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
    /** Removal of the upper half of a tall flower, done first and without shape updates. */
    private static final int REMOVE_UPPER_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** Bonemeal snapshot box around the target (grass bonemeal spreads up to 7 blocks horizontally). */
    private static final int BONEMEAL_RADIUS = 8;
    private static final int BONEMEAL_DOWN = 3;
    private static final int BONEMEAL_UP = 3;

    private static final class LevelState {
        final LongOpenHashSet loaded = new LongOpenHashSet();
        final LongLinkedOpenHashSet loadQueue = new LongLinkedOpenHashSet();
        final LongLinkedOpenHashSet sweepQueue = new LongLinkedOpenHashSet();
        long lastGameTime = Long.MIN_VALUE;
        long lastSweepAt = Long.MIN_VALUE;
        long closingSweepState = Long.MIN_VALUE;
    }

    private static final Map<ServerLevel, LevelState> STATES = new IdentityHashMap<>();

    private static boolean isTracked(net.minecraft.world.level.LevelAccessor level) {
        return level instanceof ServerLevel sl && sl.dimension().equals(Level.OVERWORLD);
    }

    private static LevelState state(ServerLevel level) {
        return STATES.computeIfAbsent(level, l -> new LevelState());
    }

    // ---------------------------------------------------------------- chunk bookkeeping

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!isTracked(event.getLevel())) {
            return;
        }
        LevelState st = state((ServerLevel) event.getLevel());
        long key = event.getChunk().getPos().toLong();
        st.loaded.add(key);
        st.loadQueue.add(key);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!isTracked(event.getLevel())) {
            return;
        }
        LevelState st = STATES.get((ServerLevel) event.getLevel());
        if (st != null) {
            long key = event.getChunk().getPos().toLong();
            st.loaded.remove(key);
            st.loadQueue.remove(key);
            st.sweepQueue.remove(key);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            STATES.remove(level);
        }
    }

    // ---------------------------------------------------------------- ticking

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!isTracked(event.getLevel())) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        LevelState st = state(level);

        long gameTime = level.getGameTime();
        long total = gameTime + SeasonSavedData.get(level).getSeasonalOffset();
        long absState = Math.floorDiv(total, Season.STATE_LENGTH_TICKS);
        long intoState = Math.floorMod(total, Season.STATE_LENGTH_TICKS);
        long yearTick = Math.floorMod(total, Season.YEAR_LENGTH_TICKS);
        Season current = Season.fromYearTick(yearTick);
        Season previous = Season.values()[(current.ordinal() + Season.STATE_COUNT - 1) % Season.STATE_COUNT];
        boolean boundaryMatters = SeasonalPlantCategory.anyChange(previous, current);

        boolean jumped = st.lastGameTime != Long.MIN_VALUE
                && (gameTime < st.lastGameTime || gameTime - st.lastGameTime > JUMP_THRESHOLD);
        st.lastGameTime = gameTime;

        if (jumped) {
            refill(st, gameTime);
        } else if (boundaryMatters && intoState < STAGGER_TICKS) {
            // Stagger window open: keep sweeping loaded chunks so positions flip close to their delay.
            if (st.sweepQueue.isEmpty() && gameTime - st.lastSweepAt >= MIN_SWEEP_INTERVAL) {
                refill(st, gameTime);
            }
        } else if (boundaryMatters && st.closingSweepState != absState) {
            // Window just closed (or the server started after it): one sweep catches the last delays.
            st.closingSweepState = absState;
            refill(st, gameTime);
        }

        int budget = CHUNKS_PER_TICK;
        while (budget > 0) {
            long key;
            if (!st.loadQueue.isEmpty()) {
                key = st.loadQueue.removeFirstLong();
            } else if (!st.sweepQueue.isEmpty()) {
                key = st.sweepQueue.removeFirstLong();
            } else {
                break;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null) {
                processChunk(level, chunk, yearTick);
                budget--;
            }
        }
    }

    private static void refill(LevelState st, long gameTime) {
        st.sweepQueue.addAll(st.loaded);
        st.lastSweepAt = gameTime;
    }

    /** Per-position flip delay after a season boundary, in [0, STAGGER_TICKS). */
    static long delayTicks(long packedPos) {
        long h = packedPos * 0x9E3779B97F4A7C15L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return Math.floorMod(h, STAGGER_TICKS);
    }

    static Season effectiveSeason(long packedPos, long yearTick) {
        return Season.fromYearTick(Math.floorMod(yearTick - delayTicks(packedPos), Season.YEAR_LENGTH_TICKS));
    }

    // ---------------------------------------------------------------- chunk processing

    private static void processChunk(ServerLevel level, LevelChunk chunk, long yearTick) {
        SeasonalPlantChunkData data = chunk.getExistingDataOrNull(ModAttachments.SEASONAL_PLANTS.get());
        boolean changed = false;

        if (data != null) {
            changed |= restoreDormant(level, chunk, data, yearTick);
            changed |= pruneStalePlayerPlaced(chunk, data);
        }

        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);
                if (top < minY) {
                    continue;
                }
                for (int y = top; y <= top + 1; y++) {
                    cursor.set(minX + dx, y, minZ + dz);
                    BlockState state = chunk.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    SeasonalPlantCategory category = SeasonalPlantCategory.of(state);
                    if (category == null) {
                        continue;
                    }
                    if (data == null) {
                        data = chunk.getData(ModAttachments.SEASONAL_PLANTS.get());
                    }
                    if (makeDormantIfOutOfSeason(level, chunk, data, cursor.immutable(), state, category, yearTick)) {
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            chunk.markUnsaved();
        }
    }

    /** Returns true if the plant was removed and recorded. */
    private static boolean makeDormantIfOutOfSeason(ServerLevel level, LevelChunk chunk, SeasonalPlantChunkData data,
                                                    BlockPos pos, BlockState state, SeasonalPlantCategory category,
                                                    long yearTick) {
        BlockPos lower = pos;
        BlockState lowerState = state;
        boolean doubleBlock = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        if (doubleBlock && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            lower = pos.below();
            lowerState = chunk.getBlockState(lower);
            if (!lowerState.is(state.getBlock())) {
                return false;
            }
        }
        long packed = lower.asLong();
        if (category.isInSeason(effectiveSeason(packed, yearTick)) || data.isPlayerPlaced(packed)) {
            return false;
        }
        if (!lowerState.getFluidState().isEmpty()) {
            return false;
        }
        if (!chunk.getNoiseBiome(QuartPos.fromBlock(lower.getX()), QuartPos.fromBlock(lower.getY()),
                QuartPos.fromBlock(lower.getZ())).is(SeasonalPlantCategory.AFFECTED_BIOMES)) {
            return false;
        }
        if (category == SeasonalPlantCategory.PUMPKINS && hasAttachedStem(level, lower)) {
            // Grown from a stem: a farm, not worldgen.
            data.playerPlaced.add(packed);
            return true;
        }

        if (doubleBlock) {
            BlockPos upper = lower.above();
            if (chunk.getBlockState(upper).is(lowerState.getBlock())) {
                level.setBlock(upper, Blocks.AIR.defaultBlockState(), REMOVE_UPPER_FLAGS);
            }
        }
        level.setBlock(lower, Blocks.AIR.defaultBlockState(), REMOVE_FLAGS);
        data.dormant.put(packed, lowerState);
        return true;
    }

    private static boolean hasAttachedStem(ServerLevel level, BlockPos pumpkin) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos n = pumpkin.relative(dir);
            LevelChunk nc = level.getChunkSource().getChunkNow(n.getX() >> 4, n.getZ() >> 4);
            if (nc == null) {
                continue;
            }
            BlockState ns = nc.getBlockState(n);
            if (ns.is(Blocks.ATTACHED_PUMPKIN_STEM) && ns.getValue(AttachedStemBlock.FACING) == dir.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    private enum RestoreResult { RESTORED, DEFER, DROP }

    private static boolean restoreDormant(ServerLevel level, LevelChunk chunk, SeasonalPlantChunkData data, long yearTick) {
        if (data.dormant.isEmpty()) {
            return false;
        }
        boolean changed = false;
        ObjectIterator<Long2ObjectMap.Entry<BlockState>> it = data.dormant.long2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Long2ObjectMap.Entry<BlockState> entry = it.next();
            long packed = entry.getLongKey();
            BlockState state = entry.getValue();
            SeasonalPlantCategory category = SeasonalPlantCategory.of(state);
            // A null category means the tags changed since it was stored: give it back rather than lose it.
            if (category != null && !category.isInSeason(effectiveSeason(packed, yearTick))) {
                continue;
            }
            RestoreResult result = tryRestore(level, chunk, BlockPos.of(packed), state);
            if (result != RestoreResult.DEFER) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static RestoreResult tryRestore(ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state) {
        RestoreResult here = spotStatus(chunk.getBlockState(pos));
        if (here != RestoreResult.RESTORED) {
            return here;
        }
        boolean doubleBlock = state.getBlock() instanceof DoublePlantBlock
                && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
        if (doubleBlock) {
            RestoreResult above = spotStatus(chunk.getBlockState(pos.above()));
            if (above != RestoreResult.RESTORED) {
                return above;
            }
        }
        if (!state.canSurvive(level, pos)) {
            return RestoreResult.DROP;
        }
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
            return RestoreResult.DEFER; // something standing in the pumpkin's spot; try next scan
        }
        if (doubleBlock) {
            DoublePlantBlock.placeAt(level, state, pos, Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        return RestoreResult.RESTORED;
    }

    /** RESTORED = the spot is free; DEFER = snow is lying there; DROP = occupied. */
    private static RestoreResult spotStatus(BlockState existing) {
        if (existing.isAir()) {
            return RestoreResult.RESTORED;
        }
        if (existing.is(Blocks.SNOW)) {
            return RestoreResult.DEFER;
        }
        if (existing.canBeReplaced() && existing.getFluidState().isEmpty()
                && !(existing.getBlock() instanceof DoublePlantBlock)) {
            return RestoreResult.RESTORED;
        }
        return RestoreResult.DROP;
    }

    /** Drops player-placed records whose block is gone (broken by water, pistons, explosions...). */
    private static boolean pruneStalePlayerPlaced(LevelChunk chunk, SeasonalPlantChunkData data) {
        if (data.playerPlaced.isEmpty()) {
            return false;
        }
        boolean changed = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        LongIterator it = data.playerPlaced.iterator();
        while (it.hasNext()) {
            cursor.set(it.nextLong());
            if (SeasonalPlantCategory.of(chunk.getBlockState(cursor)) == null) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------- player-placed tracking

    /** Records seasonal plants placed by any entity; clears the record when something else replaces one. */
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!isTracked(event.getLevel())) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState placed = event.getPlacedBlock();
        BlockPos lower = lowerHalf(pos, placed);
        LevelChunk chunk = level.getChunkAt(lower);
        if (SeasonalPlantCategory.of(placed) != null) {
            SeasonalPlantChunkData data = chunk.getData(ModAttachments.SEASONAL_PLANTS.get());
            if (data.playerPlaced.add(lower.asLong()) | data.dormant.remove(lower.asLong()) != null) {
                chunk.markUnsaved();
            }
        } else {
            forget(chunk, lower.asLong());
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!isTracked(event.getLevel())) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos lower = lowerHalf(event.getPos(), event.getState());
        forget(level.getChunkAt(lower), lower.asLong());
    }

    private static void forget(LevelChunk chunk, long packed) {
        SeasonalPlantChunkData data = chunk.getExistingDataOrNull(ModAttachments.SEASONAL_PLANTS.get());
        if (data != null && data.playerPlaced.remove(packed)) {
            chunk.markUnsaved();
        }
    }

    private static BlockPos lowerHalf(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }

    /**
     * Bonemeal (by a player or a dispenser) can sprout flowers around the target. Snapshot the seasonal
     * plants in the spread box now, and after the tick mark every new one as player-made.
     */
    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (!isTracked(event.getLevel()) || !event.isValidBonemealTarget()) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos center = event.getPos().immutable();
        LongOpenHashSet before = collectSeasonalPlants(level, center);
        MinecraftServer server = level.getServer();
        server.schedule(new TickTask(server.getTickCount(), () -> {
            LongOpenHashSet after = collectSeasonalPlants(level, center);
            LongIterator it = after.iterator();
            while (it.hasNext()) {
                long packed = it.nextLong();
                if (!before.contains(packed)) {
                    BlockPos p = BlockPos.of(packed);
                    LevelChunk chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
                    if (chunk != null) {
                        chunk.getData(ModAttachments.SEASONAL_PLANTS.get()).playerPlaced.add(packed);
                        chunk.markUnsaved();
                    }
                }
            }
        }));
    }

    /** Packed lower-half positions of seasonal plants in the bonemeal box, loaded chunks only. */
    private static LongOpenHashSet collectSeasonalPlants(ServerLevel level, BlockPos center) {
        LongOpenHashSet found = new LongOpenHashSet();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - BONEMEAL_RADIUS; x <= center.getX() + BONEMEAL_RADIUS; x++) {
            for (int z = center.getZ() - BONEMEAL_RADIUS; z <= center.getZ() + BONEMEAL_RADIUS; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }
                for (int y = center.getY() - BONEMEAL_DOWN; y <= center.getY() + BONEMEAL_UP; y++) {
                    cursor.set(x, y, z);
                    BlockState state = chunk.getBlockState(cursor);
                    if (!state.isAir() && SeasonalPlantCategory.of(state) != null) {
                        found.add(lowerHalf(cursor, state).asLong());
                    }
                }
            }
        }
        return found;
    }
}
