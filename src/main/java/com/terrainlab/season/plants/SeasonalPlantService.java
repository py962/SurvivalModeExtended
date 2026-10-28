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
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
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
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
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
 * <p>
 * In-season boost: when a category is in season at a chunk (judged at the chunk centre's effective season)
 * and the chunk has not been boosted in this in-season run, about as many extra plants as the chunk's
 * natural ones of that category (present plus dormant, excluding earlier extras and player-placed plants)
 * are picked on free surface spots of the chunk, copying the states found there. They are recorded as
 * dormant entries flagged as extras, so the normal restore puts them in the world (each at its own stagger
 * delay, with the usual free-spot and survival checks), a limited number per tick. From then on they are
 * natural plants (dormant out of season, back in season) that never count toward the next boost; each new
 * run only tops the extras back up to the natural count.
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

    /** Boost extras put into the world per tick, across all chunks of a level. */
    private static final int EXTRA_PLACEMENTS_PER_TICK = 24;
    /** Upper bound on the extras of one category in one chunk. */
    private static final int MAX_EXTRAS_PER_CATEGORY = 48;
    /** Distinct sample states kept per category while scanning a chunk. */
    private static final int MAX_SAMPLES = 32;
    /** Minimum sky light on the spot for flowers and pumpkins. */
    private static final int MIN_OPEN_SKY_LIGHT = 13;

    private static final class LevelState {
        final LongOpenHashSet loaded = new LongOpenHashSet();
        final LongLinkedOpenHashSet loadQueue = new LongLinkedOpenHashSet();
        final LongLinkedOpenHashSet sweepQueue = new LongLinkedOpenHashSet();
        long lastGameTime = Long.MIN_VALUE;
        long lastSweepAt = Long.MIN_VALUE;
        long closingSweepState = Long.MIN_VALUE;
        /** Chunks to process again next tick (boost extras waiting for placement budget). */
        final LongOpenHashSet retry = new LongOpenHashSet();
        int extraBudget;
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
        long key = event.getChunk().getPos().pack();
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
            long key = event.getChunk().getPos().pack();
            st.loaded.remove(key);
            st.loadQueue.remove(key);
            st.sweepQueue.remove(key);
            st.retry.remove(key);
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

        if (!st.retry.isEmpty()) {
            st.sweepQueue.addAll(st.retry);
            st.retry.clear();
        }
        st.extraBudget = EXTRA_PLACEMENTS_PER_TICK;

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
                processChunk(level, st, chunk, total);
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

    private static Season seasonOfState(long absState) {
        return Season.values()[(int) Math.floorMod(absState, (long) Season.STATE_COUNT)];
    }

    /**
     * The in-season run of {@code category} at a position: the absolute index of the first calendar state
     * of the current contiguous in-season stretch (at the position's effective time), or
     * {@code Long.MIN_VALUE} when the category is out of season there.
     */
    static long inSeasonRun(SeasonalPlantCategory category, long packedPos, long total) {
        long abs = Math.floorDiv(total - delayTicks(packedPos), Season.STATE_LENGTH_TICKS);
        if (!category.isInSeason(seasonOfState(abs))) {
            return Long.MIN_VALUE;
        }
        for (int i = 0; i < Season.STATE_COUNT && category.isInSeason(seasonOfState(abs - 1)); i++) {
            abs--;
        }
        return abs;
    }

    // ---------------------------------------------------------------- chunk processing

    private static void processChunk(ServerLevel level, LevelState st, LevelChunk chunk, long total) {
        long yearTick = Math.floorMod(total, Season.YEAR_LENGTH_TICKS);
        SeasonalPlantChunkData data = chunk.getExistingDataOrNull(ModAttachments.SEASONAL_PLANTS.get());
        boolean changed = false;

        if (data != null) {
            changed |= restoreDormant(level, st, chunk, data, yearTick);
            changed |= pruneStalePlayerPlaced(chunk, data);
            changed |= pruneStaleExtras(chunk, data);
        }

        // Natural (non-player, non-extra) in-season plants seen on the surface, per category, for the boost.
        int[] naturalCount = new int[CATEGORIES.length];
        @SuppressWarnings("unchecked")
        List<BlockState>[] samples = new List[CATEGORIES.length];

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
                    } else if (!isUncountedHalf(state) && !data.isPlayerPlaced(cursor.asLong())
                            && !data.extras.contains(cursor.asLong()) && isAffectedBiome(chunk, cursor)) {
                        naturalCount[category.ordinal()]++;
                        addSample(samples, category, state);
                    }
                }
            }
        }

        if (data != null && boost(level, st, chunk, data, total, naturalCount, samples)) {
            changed = true;
        }

        if (changed) {
            chunk.markUnsaved();
        }
    }

    private static final SeasonalPlantCategory[] CATEGORIES = SeasonalPlantCategory.values();

    /** Upper halves of tall flowers are counted through their lower half. */
    private static boolean isUncountedHalf(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
    }

    private static boolean isAffectedBiome(LevelChunk chunk, BlockPos pos) {
        return chunk.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ())).is(SeasonalPlantCategory.AFFECTED_BIOMES);
    }

    private static void addSample(List<BlockState>[] samples, SeasonalPlantCategory category, BlockState state) {
        List<BlockState> list = samples[category.ordinal()];
        if (list == null) {
            list = new ArrayList<>();
            samples[category.ordinal()] = list;
        }
        // Repeats are kept (up to the cap) so common variants are picked more often.
        if (list.size() < MAX_SAMPLES) {
            list.add(state);
        }
    }

    // ---------------------------------------------------------------- in-season boost

    /**
     * Tops each in-season category's extras in this chunk up to its natural count, once per in-season run.
     * New extras become dormant entries; {@link #restoreDormant} places them. Returns true if data changed.
     */
    private static boolean boost(ServerLevel level, LevelState st, LevelChunk chunk, SeasonalPlantChunkData data,
                                 long total, int[] naturalPresent, List<BlockState>[] samples) {
        ChunkPos chunkPos = chunk.getPos();
        long centre = BlockPos.asLong(chunkPos.getMiddleBlockX(), 0, chunkPos.getMiddleBlockZ());
        boolean changed = false;
        for (SeasonalPlantCategory category : CATEGORIES) {
            long run = inSeasonRun(category, centre, total);
            if (run == Long.MIN_VALUE || data.boostedRun(category) == run) {
                continue;
            }
            int natural = naturalPresent[category.ordinal()];
            int extras = 0;
            for (Long2ObjectMap.Entry<BlockState> entry : data.dormant.long2ObjectEntrySet()) {
                if (SeasonalPlantCategory.of(entry.getValue()) != category) {
                    continue;
                }
                if (data.extras.contains(entry.getLongKey())) {
                    extras++;
                } else {
                    natural++;
                    addSample(samples, category, entry.getValue());
                }
            }
            if (natural == 0) {
                continue; // nothing natural of this kind here: nothing to double, and nothing to record
            }
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            LongIterator it = data.extras.iterator();
            while (it.hasNext()) {
                long packed = it.nextLong();
                if (!data.dormant.containsKey(packed)
                        && SeasonalPlantCategory.of(chunk.getBlockState(cursor.set(packed))) == category) {
                    extras++;
                }
            }
            data.setBoostedRun(category, run);
            changed = true;
            int wanted = Math.min(natural, MAX_EXTRAS_PER_CATEGORY) - extras;
            List<BlockState> pool = samples[category.ordinal()];
            if (wanted > 0 && pool != null && !pool.isEmpty()
                    && pickExtras(level, chunk, data, category, wanted, pool, run) > 0) {
                st.retry.add(chunkPos.pack());
            }
        }
        return changed;
    }

    /** Records up to {@code wanted} extras as dormant on valid surface spots; returns how many. */
    private static int pickExtras(ServerLevel level, LevelChunk chunk, SeasonalPlantChunkData data,
                                  SeasonalPlantCategory category, int wanted, List<BlockState> pool, long run) {
        ChunkPos chunkPos = chunk.getPos();
        RandomSource random = RandomSource.create(chunkPos.pack() * 0x9E3779B97F4A7C15L ^ run * 31L ^ category.ordinal());
        int[] columns = new int[256];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = i;
        }
        for (int i = columns.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = columns[i];
            columns[i] = columns[j];
            columns[j] = t;
        }
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int placed = 0;
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos();
        for (int column : columns) {
            if (placed >= wanted) {
                break;
            }
            int dx = column & 15;
            int dz = column >> 4;
            int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);
            if (top < chunk.getMinY() || top + 2 > chunk.getMaxY()) {
                continue;
            }
            boolean canopy = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, dx, dz) > top;
            ground.set(minX + dx, top, minZ + dz);
            BlockPos spot = ground.above();
            long packed = spot.asLong();
            if (!chunk.getBlockState(spot).isAir() || data.dormant.containsKey(packed)
                    || data.isPlayerPlaced(packed) || data.extras.contains(packed)) {
                continue;
            }
            BlockState groundState = chunk.getBlockState(ground);
            if (!groundSuits(level, category, groundState, spot, canopy) || !isAffectedBiome(chunk, spot)) {
                continue;
            }
            BlockState state = pool.get(random.nextInt(pool.size()));
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
            }
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                state = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER);
                if (!chunk.getBlockState(spot.above()).isAir()) {
                    continue;
                }
            }
            if (!state.canSurvive(level, spot)) {
                continue;
            }
            data.dormant.put(packed, state);
            data.extras.add(packed);
            placed++;
        }
        return placed;
    }

    /**
     * Worldgen-like spot rules: flowers and pumpkins in the open (pumpkins on grass), leaf litter and
     * mushrooms under a leaf canopy; always on natural soil. Mushrooms' own light rule is left to canSurvive.
     */
    private static boolean groundSuits(ServerLevel level, SeasonalPlantCategory category, BlockState ground,
                                       BlockPos spot, boolean canopy) {
        return switch (category) {
            case FLOWERS -> !canopy && ground.is(BlockTags.DIRT)
                    && level.getBrightness(LightLayer.SKY, spot) >= MIN_OPEN_SKY_LIGHT;
            case PUMPKINS -> !canopy && ground.is(Blocks.GRASS_BLOCK)
                    && level.getBrightness(LightLayer.SKY, spot) >= MIN_OPEN_SKY_LIGHT;
            case LEAF_LITTER, MUSHROOMS -> canopy && ground.is(BlockTags.DIRT);
        };
    }

    /** Drops extra records whose plant is gone (and not dormant): broken, washed away, replaced. */
    private static boolean pruneStaleExtras(LevelChunk chunk, SeasonalPlantChunkData data) {
        if (data.extras.isEmpty()) {
            return false;
        }
        boolean changed = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        LongIterator it = data.extras.iterator();
        while (it.hasNext()) {
            long packed = it.nextLong();
            if (!data.dormant.containsKey(packed) && SeasonalPlantCategory.of(chunk.getBlockState(cursor.set(packed))) == null) {
                it.remove();
                changed = true;
            }
        }
        return changed;
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

    private static boolean restoreDormant(ServerLevel level, LevelState st, LevelChunk chunk, SeasonalPlantChunkData data,
                                          long yearTick) {
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
            boolean extra = data.extras.contains(packed);
            if (extra && st.extraBudget <= 0) {
                st.retry.add(chunk.getPos().pack()); // spread boost placements over ticks
                continue;
            }
            RestoreResult result = tryRestore(level, chunk, BlockPos.of(packed), state);
            if (result != RestoreResult.DEFER) {
                it.remove();
                changed = true;
                if (extra) {
                    if (result == RestoreResult.RESTORED) {
                        st.extraBudget--;
                    } else {
                        data.extras.remove(packed);
                    }
                }
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
            if (data.playerPlaced.add(lower.asLong()) | data.dormant.remove(lower.asLong()) != null
                    | data.extras.remove(lower.asLong())) {
                chunk.markUnsaved();
            }
        } else {
            forget(chunk, lower.asLong());
        }
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (!isTracked(event.getLevel())) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos lower = lowerHalf(event.getPos(), event.getState());
        forget(level.getChunkAt(lower), lower.asLong());
    }

    private static void forget(LevelChunk chunk, long packed) {
        SeasonalPlantChunkData data = chunk.getExistingDataOrNull(ModAttachments.SEASONAL_PLANTS.get());
        if (data != null && (data.playerPlaced.remove(packed) | data.extras.remove(packed))) {
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
