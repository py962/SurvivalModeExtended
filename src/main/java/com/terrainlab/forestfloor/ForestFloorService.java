package com.terrainlab.forestfloor;

import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.season.plants.SeasonalPlantChunkData;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side forest floor: fallen leaves pile up beside the trunks of seasonal trees once they
 * have turned (deepest at the end of late autumn, thinning through midwinter); a spring flower
 * carpet covers forests, plains, meadows and marshes (building through early spring, peak in late
 * spring, gone by the end of midsummer); once per season it tops up baseline mushrooms (late
 * autumn and midwinter) and flowers (spring) in forest, windswept forest and birch forest; and it
 * grows pumpkin patches in temperate biomes (building through early autumn, peak in late autumn,
 * gone by the end of midwinter; wild pumpkins otherwise grow only in cold biomes, see the
 * remove_wild_pumpkins biome modifier).
 *
 * Works a few loaded chunks per tick around players; chunks that load are queued so
 * stale leaves left in unloaded chunks are cleared promptly.
 */
public final class ForestFloorService {
    private ForestFloorService() {}

    /**
     * Block changes made here skip neighbour shape updates (UPDATE_KNOWN_SHAPE). Worldgen places mushrooms with
     * no light yet, so marshes and forests are full of mushrooms standing in daylight, which they can't survive
     * (light 13+ off mycelium or podzol). They sit there harmlessly until a neighbour shape update re-checks
     * them, and vanilla always lets that check drop the mushroom as an item (Level.setBlock clears the
     * suppress-drops flag for neighbours). Placing or removing fallen leaves, carpet flowers and mushrooms
     * doesn't change any neighbour's shape (none of them is solid or supports anything), so the update is
     * skipped; neighbours are still notified. Pumpkins are the exception (see PUMPKIN_FLAGS).
     */
    private static final int FOREST_FLOOR_FLAGS = Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE;
    /**
     * Pumpkins are full solid blocks: fences, walls, panes and redstone connect to them and torches or carpet
     * can sit on them, so their placement and removal keep vanilla's normal neighbour shape updates.
     */
    private static final int PUMPKIN_FLAGS = Block.UPDATE_ALL;

    private static final int CHUNKS_PER_TICK = 4;
    private static final int PLAYER_CHUNK_RADIUS = 6;
    private static final int REACH = 8;              // leaves fall up to 8 blocks from a canopy column
    private static final int MAX_LAYERS = 8;
    private static final int MUSHROOMS_PER_CHUNK = 3;
    private static final int FLOWERS_PER_CHUNK = 8;
    private static final int MUSHROOM_CAP = 6;       // skip the top-up if the chunk already has this many
    // Windswept forest, late autumn: a dense crop of red mushrooms in place of the baseline top-up.
    private static final int WINDSWEPT_AUTUMN_MUSHROOMS_PER_CHUNK = 10;
    private static final int WINDSWEPT_AUTUMN_MUSHROOM_CAP = 16;
    /**
     * Leaves start falling this far into the season a tree turns in: SeasonalLeaves staggers each leaf
     * block's change over its first 3 days (STAGGER_TICKS, 72,000 ticks of a 240,000-tick state), so by
     * then the whole canopy shows its autumn colour. Must match SeasonalLeaves.STAGGER_TICKS (client-only).
     */
    private static final double TURN_COMPLETE = 72_000.0 / Season.STATE_LENGTH_TICKS;
    private static final int FLOWER_CAP = 16;
    private static final int AUTUMN_PUMPKIN_CHUNK_CHANCE = 4; // about 1 chunk in 4 gets a patch

    private static final ArrayDeque<Long> LOADED_QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (event.isNewChunk()) return;
        long key = event.getChunk().getPos().toLong();
        synchronized (LOADED_QUEUE) {
            if (QUEUED.add(key)) LOADED_QUEUE.add(key);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        long offset = SeasonSavedData.get(level).getSeasonalOffset();
        long gameTime = level.getGameTime();
        long yearTick = SeasonManager.yearRelativeTick(gameTime, offset);
        Season season = Season.fromYearTick(yearTick);
        double stateProgress = SeasonManager.getStateProgress(gameTime, offset);
        double[] progress = new double[LeafSource.values().length];
        for (LeafSource tree : LeafSource.values()) {
            progress[tree.ordinal()] = litterLevel(tree, season, stateProgress);
        }
        double carpet = carpetLevel(season, stateProgress);
        double pumpkins = pumpkinLevel(season, stateProgress);
        long runKey = Math.floorDiv(gameTime + offset, Season.YEAR_LENGTH_TICKS) * 8 + season.ordinal();

        RandomSource random = level.getRandom();
        int budget = CHUNKS_PER_TICK;

        // Freshly loaded chunks first, so old leaves don't linger after midwinter.
        while (budget > 0) {
            Long key;
            synchronized (LOADED_QUEUE) {
                key = LOADED_QUEUE.poll();
                if (key != null) QUEUED.remove(key);
            }
            if (key == null) break;
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null) continue;
            processChunk(level, chunk, season, progress, carpet, pumpkins, runKey, random);
            budget--;
        }

        for (; budget > 0; budget--) {
            ServerPlayer player = players.get(random.nextInt(players.size()));
            int cx = (player.getBlockX() >> 4) + random.nextInt(PLAYER_CHUNK_RADIUS * 2 + 1) - PLAYER_CHUNK_RADIUS;
            int cz = (player.getBlockZ() >> 4) + random.nextInt(PLAYER_CHUNK_RADIUS * 2 + 1) - PLAYER_CHUNK_RADIUS;
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            processChunk(level, chunk, season, progress, carpet, pumpkins, runKey, random);
        }
    }

    private static void processChunk(ServerLevel level, LevelChunk chunk, Season season, double[] progress,
                                     double carpet, double pumpkins, long runKey, RandomSource random) {
        if (Arrays.stream(progress).anyMatch(p -> p > 0.0)) {
            accumulate(level, chunk, progress);
        } else {
            clear(level, chunk);
        }
        updateCarpet(level, chunk, carpet);
        if (pumpkins > 0.0) {
            updateAutumnPumpkins(level, chunk, pumpkins);
        } else if (!chunk.getData(ForestFloor.AUTUMN_PUMPKINS).isEmpty()) {
            removeAutumnPumpkins(level, chunk);
        }
        boolean spring = season == Season.EARLY_SPRING || season == Season.LATE_SPRING;
        // Same window as SeasonalPlantCategory.MUSHROOMS, which then fades these in and out like natural ones.
        boolean mushroomSeason = season == Season.LATE_AUTUMN || season == Season.MIDWINTER;
        boolean patchSeason = season == Season.EARLY_AUTUMN || season == Season.LATE_AUTUMN;
        if ((spring || mushroomSeason || patchSeason) && chunk.getData(ForestFloor.EXTRAS_RUN) != runKey) {
            ChunkPos cp = chunk.getPos();
            BlockPos centre = new BlockPos(cp.getMiddleBlockX(), level.getSeaLevel(), cp.getMiddleBlockZ());
            Holder<Biome> biome = level.getBiome(centre);
            // New patches are planned in autumn only; midwinter just lets the existing ones fade out.
            if (patchSeason && chunk.getData(ForestFloor.AUTUMN_PUMPKINS).isEmpty()
                    && biome.is(ForestFloor.AUTUMN_PUMPKIN_BIOMES)
                    && hasPumpkinPatchThisYear(level, chunk, runKey)) {
                planAutumnPumpkins(level, chunk, runKey);
                updateAutumnPumpkins(level, chunk, pumpkins);
            }
            if (biome.is(ForestFloor.FOREST_FLOOR_EXTRAS)) {
                if (season == Season.LATE_AUTUMN && biome.is(Biomes.WINDSWEPT_FOREST)) {
                    addMushrooms(level, chunk, random, WINDSWEPT_AUTUMN_MUSHROOMS_PER_CHUNK,
                            WINDSWEPT_AUTUMN_MUSHROOM_CAP, true);
                } else if (mushroomSeason) {
                    addMushrooms(level, chunk, random, MUSHROOMS_PER_CHUNK, MUSHROOM_CAP, false);
                }
                if (spring) addFlowers(level, chunk, biome, random);
            }
            chunk.setData(ForestFloor.EXTRAS_RUN, runKey);
            chunk.markUnsaved();
        }
    }

    // ---------------------------------------------------------------- fallen leaves

    /** Ground y (the first non-motion-blocking block, ignoring leaves) for a world column. */
    private static int surfaceY(LevelChunk chunk, int x, int z) {
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    private static void clear(ServerLevel level, LevelChunk chunk) {
        int bx = chunk.getPos().getMinBlockX();
        int bz = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                pos.set(bx + dx, surfaceY(chunk, bx + dx, bz + dz), bz + dz);
                if (chunk.getBlockState(pos).is(ForestFloor.FALLEN_LEAVES.get())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FOREST_FLOOR_FLAGS);
                }
            }
        }
    }

    /** Piles beside trunks: a full block next to the log, about 2 layers 4 blocks out. */
    private static final double TRUNK_REACH = 4.0;
    /** Light scatter: 2 layers under the canopy, fading to nothing at REACH. */
    private static final double CANOPY_LAYERS = 2.0;

    private static void accumulate(ServerLevel level, LevelChunk chunk, double[] progress) {
        int bx = chunk.getPos().getMinBlockX();
        int bz = chunk.getPos().getMinBlockZ();
        int cx = bx >> 4, cz = bz >> 4;
        int size = 16 + REACH * 2;
        // Seasonal canopy columns in the chunk plus an 8-block margin (from loaded neighbours), and which of
        // them stand on a trunk (a log as the top non-leaf block under the leaves).
        LeafSource[] canopy = new LeafSource[size * size];
        boolean[] trunk = new boolean[size * size];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ax = 0; ax < size; ax++) {
            for (int az = 0; az < size; az++) {
                int wx = bx - REACH + ax;
                int wz = bz - REACH + az;
                LevelChunk c = (wx >> 4) == cx && (wz >> 4) == cz
                        ? chunk : level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
                if (c == null) continue;
                int top = c.getHeight(Heightmap.Types.MOTION_BLOCKING, wx & 15, wz & 15);
                int ground = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx & 15, wz & 15);
                if (top <= ground) continue;
                pos.set(wx, top, wz);
                LeafSource src = sourceOf(level, c.getBlockState(pos), pos);
                if (src == null) continue;
                canopy[ax * size + az] = src;
                pos.set(wx, ground, wz);
                trunk[ax * size + az] = c.getBlockState(pos).is(BlockTags.LOGS);
            }
        }

        // Nearest seasonal canopy column and nearest trunk (and their trees) for each column of this chunk.
        float[] canopyDist = new float[256];
        float[] trunkDist = new float[256];
        LeafSource[] canopyTree = new LeafSource[256];
        LeafSource[] trunkTree = new LeafSource[256];
        Arrays.fill(canopyDist, Float.MAX_VALUE);
        Arrays.fill(trunkDist, Float.MAX_VALUE);
        for (int ax = 0; ax < size; ax++) {
            for (int az = 0; az < size; az++) {
                LeafSource src = canopy[ax * size + az];
                if (src == null) continue;
                boolean isTrunk = trunk[ax * size + az];
                int lx = ax - REACH, lz = az - REACH;
                for (int dx = Math.max(0, lx - REACH); dx <= Math.min(15, lx + REACH); dx++) {
                    for (int dz = Math.max(0, lz - REACH); dz <= Math.min(15, lz + REACH); dz++) {
                        float d = (float) Math.sqrt((dx - lx) * (dx - lx) + (dz - lz) * (dz - lz));
                        int i = dx * 16 + dz;
                        if (d <= REACH && d < canopyDist[i]) {
                            canopyDist[i] = d;
                            canopyTree[i] = src;
                        }
                        if (isTrunk && d <= TRUNK_REACH && d < trunkDist[i]) {
                            trunkDist[i] = d;
                            trunkTree[i] = src;
                        }
                    }
                }
            }
        }

        Block fallen = ForestFloor.FALLEN_LEAVES.get();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int i = dx * 16 + dz;
                if (canopyTree[i] == null) continue;
                int wx = bx + dx, wz = bz + dz;
                double dt = trunkDist[i];
                double trunkDepth = dt <= 1.5 ? MAX_LAYERS
                        : dt <= TRUNK_REACH ? MAX_LAYERS - (MAX_LAYERS - 2) * (dt - 1.5) / (TRUNK_REACH - 1.5) : 0.0;
                double canopyDepth = CANOPY_LAYERS * (1.0 - canopyDist[i] / REACH);
                double depth = Math.max(trunkDepth, canopyDepth);
                LeafSource tree = trunkDepth >= canopyDepth && trunkTree[i] != null ? trunkTree[i] : canopyTree[i];
                double noise = (hash(wx, wz) - 0.5) * 2.0;
                int target = Math.min(MAX_LAYERS, (int) Math.round(progress[tree.ordinal()] * (depth + noise)));

                pos.set(wx, surfaceY(chunk, wx, wz), wz);
                BlockState here = chunk.getBlockState(pos);
                if (here.is(fallen)) {
                    int layers = here.getValue(FallenLeavesBlock.LAYERS);
                    if (target < 1) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FOREST_FLOOR_FLAGS);
                    } else if (layers < target) {
                        level.setBlock(pos, here.setValue(FallenLeavesBlock.LAYERS, layers + 1), FOREST_FLOOR_FLAGS);
                    } else if (layers > target) {
                        level.setBlock(pos, here.setValue(FallenLeavesBlock.LAYERS, layers - 1), FOREST_FLOOR_FLAGS);
                    }
                } else if (target >= 1 && (here.isAir() || here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN))) {
                    BlockState state = fallen.defaultBlockState().setValue(FallenLeavesBlock.TREE, tree);
                    if (validGround(level, pos) && state.canSurvive(level, pos)) {
                        level.setBlock(pos, state, FOREST_FLOOR_FLAGS);
                    }
                }
            }
        }
    }

    private static boolean validGround(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return ground.getFluidState().isEmpty()
                && !ground.is(BlockTags.LEAVES)
                && !ground.is(Blocks.SNOW_BLOCK) && !ground.is(Blocks.POWDER_SNOW)
                && level.getFluidState(pos).isEmpty()
                && ground.isFaceSturdy(level, below, Direction.UP);
    }

    private static LeafSource sourceOf(ServerLevel level, BlockState leaves, BlockPos pos) {
        if (leaves.is(Blocks.BIRCH_LEAVES)) return LeafSource.BIRCH;
        if (leaves.is(Blocks.CHERRY_LEAVES)) return LeafSource.CHERRY;
        if (leaves.is(com.terrainlab.registry.ModBlocks.RED_POPLAR_LEAVES.get())
                || leaves.is(com.terrainlab.registry.ModBlocks.ORANGE_POPLAR_LEAVES.get())
                || leaves.is(com.terrainlab.registry.ModBlocks.YELLOW_POPLAR_LEAVES.get())) return LeafSource.POPLAR;
        if (leaves.is(Blocks.OAK_LEAVES) && level.getBiome(pos).is(ForestFloor.SEASONAL_OAKS)) return LeafSource.OAK;
        return null;
    }

    private static double hash(int x, int z) {
        long h = x * 341873128712L + z * 132897987541L;
        h = (h ^ (h >>> 29)) * 0x5DEECE66DL;
        h ^= h >>> 32;
        return (h & 0xFFFF) / 65535.0;
    }

    // ---------------------------------------------------------------- seasonal curves

    /**
     * Leaf-litter fill (0..1) under one kind of tree. Nothing falls until the tree has fully turned (see
     * {@link #TURN_COMPLETE}): oak, cherry and poplar turn at the start of early autumn, birch (green
     * through early autumn) at the start of late autumn. From there the litter builds to its peak at the
     * end of late autumn, then thins through midwinter.
     */
    private static double litterLevel(LeafSource tree, Season season, double p) {
        boolean lateTurner = tree == LeafSource.BIRCH;
        return switch (season) {
            case EARLY_AUTUMN -> lateTurner ? 0.0 : 0.35 * sinceTurned(p);
            case LATE_AUTUMN -> lateTurner ? sinceTurned(p) : 0.35 + 0.65 * p;
            case MIDWINTER -> 0.6 * (1.0 - p);
            default -> 0.0;
        };
    }

    /** 0 until {@link #TURN_COMPLETE} into the state, then rising linearly to 1 at its end. */
    private static double sinceTurned(double p) {
        return Math.max(0.0, (p - TURN_COMPLETE) / (1.0 - TURN_COMPLETE));
    }

    /** Spring flower carpet (0..1): builds through early spring, peaks in late spring, gone by the end of midsummer. */
    private static double carpetLevel(Season season, double p) {
        return switch (season) {
            case EARLY_SPRING -> p;
            case LATE_SPRING -> 1.0;
            case MIDSUMMER -> 1.0 - p;
            default -> 0.0;
        };
    }

    /** Autumn pumpkin patches (0..1): build through early autumn, peak in late autumn, gone by the end of midwinter. */
    private static double pumpkinLevel(Season season, double p) {
        return switch (season) {
            case EARLY_AUTUMN -> p;
            case LATE_AUTUMN -> 1.0;
            case MIDWINTER -> 1.0 - p;
            default -> 0.0;
        };
    }

    // ---------------------------------------------------------------- spring flower carpet

    private static final double DENSE_CARPET = 0.6;   // share of open grass at the peak: forests
    private static final double OPEN_CARPET = 0.4;    // plains, meadow, marsh
    private static final int CARPET_CHANGES_PER_PASS = 48;

    private static final Block[] FOREST_CARPET = {Blocks.DANDELION, Blocks.POPPY, Blocks.LILY_OF_THE_VALLEY};
    private static final Block[] BIRCH_CARPET = {Blocks.DANDELION, Blocks.POPPY, Blocks.LILY_OF_THE_VALLEY, Blocks.WILDFLOWERS};
    private static final Block[] PLAINS_CARPET = {Blocks.DANDELION, Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY,
            Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP};
    private static final Block[] MEADOW_CARPET = {Blocks.ALLIUM, Blocks.CORNFLOWER, Blocks.DANDELION, Blocks.OXEYE_DAISY, Blocks.POPPY};

    private static double carpetDensity(Holder<Biome> biome) {
        if (biome.is(ForestFloor.SPRING_CARPET_DENSE)) return DENSE_CARPET;
        if (biome.is(ForestFloor.SPRING_CARPET_BIOMES)) return OPEN_CARPET;
        return 0.0;
    }

    private static Block[] carpetPalette(Holder<Biome> biome) {
        if (biome.is(Biomes.PLAINS)) return PLAINS_CARPET;
        if (biome.is(Biomes.MEADOW) || biome.is(ForestFloor.MARSH)) return MEADOW_CARPET;
        if (biome.is(Biomes.BIRCH_FOREST) || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) return BIRCH_CARPET;
        return FOREST_CARPET;
    }

    private static boolean isCarpetFlower(BlockState state) {
        return state.is(BlockTags.SMALL_FLOWERS) || state.is(Blocks.WILDFLOWERS);
    }

    /**
     * Places and removes this chunk's carpet flowers so that, per biome, the share of open grass columns
     * carrying one follows {@code level * density}. Each column has a fixed threshold, so flowers fill in and
     * fade out in a stable order. Only flowers this service placed (tracked per chunk) are ever removed, and
     * they are marked as player-placed for the seasonal plant service so it leaves them to this one.
     */
    private static void updateCarpet(ServerLevel level, LevelChunk chunk, double carpet) {
        List<Long> tracked = chunk.getData(ForestFloor.SPRING_CARPET);
        if (carpet <= 0.0 && tracked.isEmpty()) return;
        Set<Long> mine = new HashSet<>(tracked);
        boolean changed = false;
        int changes = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Long packed : tracked) {
            pos.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
            if (!isCarpetFlower(chunk.getBlockState(pos))) {
                mine.remove(packed);
                changed = true;
                continue;
            }
            if (changes >= CARPET_CHANGES_PER_PASS) continue;
            if (carpetHash(pos.getX(), pos.getZ()) >= carpet * carpetDensity(level.getBiome(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FOREST_FLOOR_FLAGS);
                mine.remove(packed);
                changed = true;
                changes++;
            }
        }

        if (carpet > 0.0) {
            int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
            SeasonalPlantChunkData plants = null;
            for (int dx = 0; dx < 16 && changes < CARPET_CHANGES_PER_PASS; dx++) {
                for (int dz = 0; dz < 16 && changes < CARPET_CHANGES_PER_PASS; dz++) {
                    int x = bx + dx, z = bz + dz;
                    double h = carpetHash(x, z);
                    if (h >= carpet * DENSE_CARPET) continue;
                    pos.set(x, surfaceY(chunk, x, z), z);
                    if (mine.contains(pos.asLong())) continue;
                    Holder<Biome> biome = level.getBiome(pos);
                    if (h >= carpet * carpetDensity(biome)) continue;
                    if (!chunk.getBlockState(pos).isAir() || !chunk.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) continue;
                    BlockState flower = carpetFlower(carpetPalette(biome), x, z);
                    if (!flower.canSurvive(level, pos)) continue;
                    level.setBlock(pos, flower, FOREST_FLOOR_FLAGS);
                    long packed = pos.asLong();
                    mine.add(packed);
                    if (plants == null) plants = chunk.getData(ModAttachments.SEASONAL_PLANTS.get());
                    plants.markPlayerPlaced(packed);
                    changed = true;
                    changes++;
                }
            }
        }

        if (changed) {
            chunk.setData(ForestFloor.SPRING_CARPET, List.copyOf(mine));
            chunk.markUnsaved();
        }
    }

    /** Patchy choice: one flower type per 4x4 cell, with about a quarter of columns mixed in at random. */
    private static BlockState carpetFlower(Block[] palette, int x, int z) {
        double mix = hash(x * 7 + 13, z * 7 - 5);
        int i = mix < 0.25
                ? (int) (hash(x - 91, z + 37) * palette.length)
                : (int) (hash(x >> 2, (z >> 2) + 4099) * palette.length);
        Block block = palette[Math.min(i, palette.length - 1)];
        BlockState state = block.defaultBlockState();
        if (block == Blocks.WILDFLOWERS) {
            state = state.setValue(FlowerBedBlock.AMOUNT, 4)
                    .setValue(FlowerBedBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(RandomSource.create(x * 31L + z)));
        }
        return state;
    }

    private static double carpetHash(int x, int z) {
        return hash(x + 7919, z - 104729);
    }

    // ---------------------------------------------------------------- mushrooms and flowers

    private static void addMushrooms(ServerLevel level, LevelChunk chunk, RandomSource random,
                                     int perChunk, int cap, boolean allRed) {
        int existing = countSurface(level, chunk, s -> s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM));
        if (existing >= cap) return;
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < perChunk * 10 && placed < Math.min(perChunk, cap - existing); attempt++) {
            int x = bx + random.nextInt(16), z = bz + random.nextInt(16);
            // Shaded ground only: under a canopy.
            if (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15)
                    <= chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15)) continue;
            pos.set(x, surfaceY(chunk, x, z), z);
            BlockState here = level.getBlockState(pos);
            if (!here.isAir() && !here.is(ForestFloor.FALLEN_LEAVES.get())) continue;
            BlockState mushroom = (allRed || random.nextInt(3) == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
            if (!mushroom.canSurvive(level, pos)) continue;
            level.setBlock(pos, mushroom, FOREST_FLOOR_FLAGS);
            placed++;
        }
    }

    private static void addFlowers(ServerLevel level, LevelChunk chunk, Holder<Biome> biome, RandomSource random) {
        int existing = countSurface(level, chunk, s -> s.is(BlockTags.SMALL_FLOWERS));
        if (existing >= FLOWER_CAP) return;
        Block[] palette = biome.is(Biomes.WINDSWEPT_FOREST)
                ? new Block[]{Blocks.DANDELION, Blocks.POPPY}
                : new Block[]{Blocks.DANDELION, Blocks.POPPY, Blocks.LILY_OF_THE_VALLEY};
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Two clumps of four, one flower type per clump.
        for (int clump = 0; clump < 4 && placed < FLOWERS_PER_CHUNK; clump++) {
            int cx = bx + 3 + random.nextInt(10), cz = bz + 3 + random.nextInt(10);
            BlockState flower = palette[random.nextInt(palette.length)].defaultBlockState();
            int inClump = 0;
            for (int attempt = 0; attempt < 12 && inClump < 4 && placed < FLOWERS_PER_CHUNK; attempt++) {
                int x = cx + random.nextInt(5) - 2, z = cz + random.nextInt(5) - 2;
                if ((x >> 4) != chunk.getPos().x || (z >> 4) != chunk.getPos().z) continue;
                // Open grass only: no canopy overhead.
                if (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15)
                        > chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15)) continue;
                pos.set(x, surfaceY(chunk, x, z), z);
                if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) continue;
                if (!flower.canSurvive(level, pos)) continue;
                level.setBlock(pos, flower, FOREST_FLOOR_FLAGS);
                inClump++;
                placed++;
            }
        }
    }

    /**
     * Whether this chunk grows a pumpkin patch this year: about 1 in {@link #AUTUMN_PUMPKIN_CHUNK_CHANCE},
     * decided once per chunk per year (a hash, not a roll per pass, so the odds don't stack up across the
     * two seasons a patch can be planned in).
     */
    private static boolean hasPumpkinPatchThisYear(ServerLevel level, LevelChunk chunk, long runKey) {
        long year = Math.floorDiv(runKey, 8);
        int cx = chunk.getPos().getMinBlockX() >> 4, cz = chunk.getPos().getMinBlockZ() >> 4;
        return (int) (hash(cx * 31 + (int) year * 7919, cz * 17 - (int) year * 104729) * AUTUMN_PUMPKIN_CHUNK_CHANCE) == 0;
    }

    /**
     * Picks the spots for a patch of 3-8 pumpkins on open grass and remembers them; nothing is placed here.
     * {@link #updateAutumnPumpkins} then shows each one while its fixed threshold is below the season's
     * pumpkin level, so the patch fills in through early autumn and thins out through midwinter.
     */
    private static void planAutumnPumpkins(ServerLevel level, LevelChunk chunk, long runKey) {
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        RandomSource random = RandomSource.create(chunk.getPos().getMinBlockX() * 341873128712L
                + chunk.getPos().getMinBlockZ() * 132897987541L + Math.floorDiv(runKey, 8));
        int cx = bx + 3 + random.nextInt(10), cz = bz + 3 + random.nextInt(10);
        int want = 3 + random.nextInt(6);
        java.util.List<Long> planned = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 40 && want > 0; attempt++) {
            int x = cx + random.nextInt(7) - 3, z = cz + random.nextInt(7) - 3;
            if ((x >> 4) != (bx >> 4) || (z >> 4) != (bz >> 4)) continue;
            pos.set(x, surfaceY(chunk, x, z), z);
            if (planned.contains(pos.asLong()) || !pumpkinSpot(level, pos)) continue;
            planned.add(pos.asLong());
            want--;
        }
        if (planned.isEmpty()) return;
        chunk.setData(ForestFloor.AUTUMN_PUMPKINS, java.util.List.copyOf(planned));
        chunk.markUnsaved();
    }

    private static boolean pumpkinSpot(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        return (here.isAir() || here.is(Blocks.SHORT_GRASS)) && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK);
    }

    /**
     * Shows or hides each planned pumpkin of this chunk's patch for the current pumpkin level. Pumpkins this
     * service places are marked as player-placed for the seasonal plant service, so it leaves them to this one.
     */
    private static void updateAutumnPumpkins(ServerLevel level, LevelChunk chunk, double pumpkins) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState pumpkin = Blocks.PUMPKIN.defaultBlockState();
        SeasonalPlantChunkData plants = null;
        for (long packed : chunk.getData(ForestFloor.AUTUMN_PUMPKINS)) {
            pos.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
            boolean shown = hash(pos.getX() - 4271, pos.getZ() + 9973) < pumpkins;
            boolean present = level.getBlockState(pos).is(Blocks.PUMPKIN);
            if (shown && !present && pumpkinSpot(level, pos)) {
                level.setBlock(pos, pumpkin, PUMPKIN_FLAGS);
                if (plants == null) plants = chunk.getData(ModAttachments.SEASONAL_PLANTS.get());
                plants.markPlayerPlaced(packed);
                chunk.markUnsaved();
            } else if (!shown && present) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), PUMPKIN_FLAGS);
            }
        }
    }

    private static void removeAutumnPumpkins(ServerLevel level, LevelChunk chunk) {
        for (long packed : chunk.getData(ForestFloor.AUTUMN_PUMPKINS)) {
            BlockPos pos = BlockPos.of(packed);
            if (level.getBlockState(pos).is(Blocks.PUMPKIN)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), PUMPKIN_FLAGS);
            }
        }
        chunk.setData(ForestFloor.AUTUMN_PUMPKINS, java.util.List.of());
        chunk.markUnsaved();
    }

    private static int countSurface(ServerLevel level, LevelChunk chunk, java.util.function.Predicate<BlockState> test) {
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                pos.set(bx + dx, surfaceY(chunk, bx + dx, bz + dz), bz + dz);
                if (test.test(chunk.getBlockState(pos))) count++;
            }
        }
        return count;
    }
}
