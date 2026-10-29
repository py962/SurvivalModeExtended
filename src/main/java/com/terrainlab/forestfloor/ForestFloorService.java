package com.terrainlab.forestfloor;

import com.terrainlab.data.SeasonSavedData;
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
 * Server-side forest floor: builds up fallen leaves under seasonal trees through early
 * autumn, clears them from midwinter on, and once per season tops up baseline mushrooms
 * (autumn) and flowers (spring) in forest, windswept forest and birch forest, and puts
 * pumpkin patches in temperate biomes for the autumn only (wild pumpkins otherwise grow
 * only in cold biomes; see the remove_wild_pumpkins biome modifier).
 *
 * Works a few loaded chunks per tick around players; chunks that load are queued so
 * stale leaves left in unloaded chunks are cleared promptly.
 */
public final class ForestFloorService {
    private ForestFloorService() {}

    private static final int CHUNKS_PER_TICK = 4;
    private static final int PLAYER_CHUNK_RADIUS = 6;
    private static final int REACH = 8;              // leaves fall up to 8 blocks from a canopy column
    private static final int MAX_LAYERS = 8;
    private static final int MUSHROOMS_PER_CHUNK = 3;
    private static final int FLOWERS_PER_CHUNK = 8;
    private static final int MUSHROOM_CAP = 6;       // skip the top-up if the chunk already has this many
    private static final int FLOWER_CAP = 16;
    private static final int AUTUMN_PUMPKIN_CHUNK_CHANCE = 4; // about 1 chunk in 4 gets a patch

    private static final ArrayDeque<Long> LOADED_QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (event.isNewChunk()) return;
        long key = event.getChunk().getPos().pack();
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
        double progress = switch (season) {
            case EARLY_AUTUMN -> SeasonManager.getStateProgress(gameTime, offset);
            case LATE_AUTUMN -> 1.0;
            default -> 0.0;
        };
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
            processChunk(level, chunk, season, progress, runKey, random);
            budget--;
        }

        for (; budget > 0; budget--) {
            ServerPlayer player = players.get(random.nextInt(players.size()));
            int cx = (player.getBlockX() >> 4) + random.nextInt(PLAYER_CHUNK_RADIUS * 2 + 1) - PLAYER_CHUNK_RADIUS;
            int cz = (player.getBlockZ() >> 4) + random.nextInt(PLAYER_CHUNK_RADIUS * 2 + 1) - PLAYER_CHUNK_RADIUS;
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            processChunk(level, chunk, season, progress, runKey, random);
        }
    }

    private static void processChunk(ServerLevel level, LevelChunk chunk, Season season, double progress,
                                     long runKey, RandomSource random) {
        if (progress > 0.0) {
            accumulate(level, chunk, progress);
        } else if (season != Season.LATE_AUTUMN) {
            clear(level, chunk);
        }
        boolean autumn = season == Season.EARLY_AUTUMN || season == Season.LATE_AUTUMN;
        if (!autumn && !chunk.getData(ForestFloor.AUTUMN_PUMPKINS).isEmpty()) {
            removeAutumnPumpkins(level, chunk);
        }
        boolean spring = season == Season.EARLY_SPRING || season == Season.LATE_SPRING;
        if ((autumn || spring) && chunk.getData(ForestFloor.EXTRAS_RUN) != runKey) {
            ChunkPos cp = chunk.getPos();
            BlockPos centre = new BlockPos(cp.getMiddleBlockX(), level.getSeaLevel(), cp.getMiddleBlockZ());
            Holder<Biome> biome = level.getBiome(centre);
            if (season == Season.EARLY_AUTUMN && biome.is(ForestFloor.AUTUMN_PUMPKIN_BIOMES)
                    && random.nextInt(AUTUMN_PUMPKIN_CHUNK_CHANCE) == 0) {
                addAutumnPumpkins(level, chunk, random);
            }
            if (biome.is(ForestFloor.FOREST_FLOOR_EXTRAS)) {
                if (autumn) addMushrooms(level, chunk, random);
                else addFlowers(level, chunk, biome, random);
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
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void accumulate(ServerLevel level, LevelChunk chunk, double progress) {
        int bx = chunk.getPos().getMinBlockX();
        int bz = chunk.getPos().getMinBlockZ();
        int size = 16 + REACH * 2;
        // Seasonal canopy columns in the chunk plus an 8-block margin (from loaded neighbours).
        LeafSource[] canopy = new LeafSource[size * size];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ax = 0; ax < size; ax++) {
            for (int az = 0; az < size; az++) {
                int wx = bx - REACH + ax;
                int wz = bz - REACH + az;
                LevelChunk c = (wx >> 4) == chunk.getPos().x() && (wz >> 4) == chunk.getPos().z()
                        ? chunk : level.getChunkSource().getChunkNow(wx >> 4, wz >> 4);
                if (c == null) continue;
                int top = c.getHeight(Heightmap.Types.MOTION_BLOCKING, wx & 15, wz & 15);
                int ground = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx & 15, wz & 15);
                if (top <= ground) continue;
                pos.set(wx, top, wz);
                canopy[ax * size + az] = sourceOf(level, c.getBlockState(pos), pos);
            }
        }

        // Nearest seasonal canopy column (and its tree) for each column of this chunk.
        float[] dist = new float[256];
        LeafSource[] tree = new LeafSource[256];
        Arrays.fill(dist, Float.MAX_VALUE);
        for (int ax = 0; ax < size; ax++) {
            for (int az = 0; az < size; az++) {
                LeafSource src = canopy[ax * size + az];
                if (src == null) continue;
                int lx = ax - REACH, lz = az - REACH;
                for (int dx = Math.max(0, lx - REACH); dx <= Math.min(15, lx + REACH); dx++) {
                    for (int dz = Math.max(0, lz - REACH); dz <= Math.min(15, lz + REACH); dz++) {
                        float d = (float) Math.sqrt((dx - lx) * (dx - lx) + (dz - lz) * (dz - lz));
                        int i = dx * 16 + dz;
                        if (d <= REACH && d < dist[i]) {
                            dist[i] = d;
                            tree[i] = src;
                        }
                    }
                }
            }
        }

        Block fallen = ForestFloor.FALLEN_LEAVES.get();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int i = dx * 16 + dz;
                if (tree[i] == null) continue;
                int wx = bx + dx, wz = bz + dz;
                // Full block under the canopy, thinning to 1 layer at 8 blocks, with per-column noise.
                double depth = MAX_LAYERS - (MAX_LAYERS - 1) * dist[i] / REACH;
                double noise = (hash(wx, wz) - 0.5) * 3.0;
                int target = (int) Math.round(progress * (depth + noise));
                if (target < 1) continue;
                target = Math.min(MAX_LAYERS, target);

                pos.set(wx, surfaceY(chunk, wx, wz), wz);
                BlockState here = chunk.getBlockState(pos);
                if (here.is(fallen)) {
                    int layers = here.getValue(FallenLeavesBlock.LAYERS);
                    if (layers < target) {
                        level.setBlock(pos, here.setValue(FallenLeavesBlock.LAYERS, layers + 1), Block.UPDATE_CLIENTS);
                    }
                } else if (here.isAir() || here.is(Blocks.SHORT_GRASS) || here.is(Blocks.FERN)) {
                    BlockState state = fallen.defaultBlockState().setValue(FallenLeavesBlock.TREE, tree[i]);
                    if (validGround(level, pos) && state.canSurvive(level, pos)) {
                        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
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
        if (leaves.is(Blocks.RED_POPLAR_LEAVES) || leaves.is(Blocks.ORANGE_POPLAR_LEAVES)
                || leaves.is(Blocks.YELLOW_POPLAR_LEAVES)) return LeafSource.POPLAR;
        if (leaves.is(Blocks.OAK_LEAVES) && level.getBiome(pos).is(ForestFloor.SEASONAL_OAKS)) return LeafSource.OAK;
        return null;
    }

    private static double hash(int x, int z) {
        long h = x * 341873128712L + z * 132897987541L;
        h = (h ^ (h >>> 29)) * 0x5DEECE66DL;
        h ^= h >>> 32;
        return (h & 0xFFFF) / 65535.0;
    }

    // ---------------------------------------------------------------- mushrooms and flowers

    private static void addMushrooms(ServerLevel level, LevelChunk chunk, RandomSource random) {
        int existing = countSurface(level, chunk, s -> s.is(Blocks.BROWN_MUSHROOM) || s.is(Blocks.RED_MUSHROOM));
        if (existing >= MUSHROOM_CAP) return;
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int placed = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 32 && placed < MUSHROOMS_PER_CHUNK; attempt++) {
            int x = bx + random.nextInt(16), z = bz + random.nextInt(16);
            // Shaded ground only: under a canopy.
            if (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15)
                    <= chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15)) continue;
            pos.set(x, surfaceY(chunk, x, z), z);
            BlockState here = level.getBlockState(pos);
            if (!here.isAir() && !here.is(ForestFloor.FALLEN_LEAVES.get())) continue;
            BlockState mushroom = (random.nextInt(3) == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
            if (!mushroom.canSurvive(level, pos)) continue;
            level.setBlock(pos, mushroom, Block.UPDATE_CLIENTS);
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
                if ((x >> 4) != chunk.getPos().x() || (z >> 4) != chunk.getPos().z()) continue;
                // Open grass only: no canopy overhead.
                if (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15)
                        > chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15)) continue;
                pos.set(x, surfaceY(chunk, x, z), z);
                if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) continue;
                if (!flower.canSurvive(level, pos)) continue;
                level.setBlock(pos, flower, Block.UPDATE_CLIENTS);
                inClump++;
                placed++;
            }
        }
    }

    /** A patch of 3-8 pumpkins on open grass, remembered so midwinter can take them away. */
    private static void addAutumnPumpkins(ServerLevel level, LevelChunk chunk, RandomSource random) {
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        int cx = bx + 3 + random.nextInt(10), cz = bz + 3 + random.nextInt(10);
        int want = 3 + random.nextInt(6);
        java.util.List<Long> placed = new java.util.ArrayList<>(chunk.getData(ForestFloor.AUTUMN_PUMPKINS));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState pumpkin = Blocks.PUMPKIN.defaultBlockState();
        for (int attempt = 0; attempt < 40 && want > 0; attempt++) {
            int x = cx + random.nextInt(7) - 3, z = cz + random.nextInt(7) - 3;
            if ((x >> 4) != (bx >> 4) || (z >> 4) != (bz >> 4)) continue;
            pos.set(x, surfaceY(chunk, x, z), z);
            BlockState here = level.getBlockState(pos);
            if (!(here.isAir() || here.is(Blocks.SHORT_GRASS)) || !level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK)) continue;
            level.setBlock(pos, pumpkin, Block.UPDATE_CLIENTS);
            placed.add(pos.asLong());
            want--;
        }
        chunk.setData(ForestFloor.AUTUMN_PUMPKINS, java.util.List.copyOf(placed));
    }

    private static void removeAutumnPumpkins(ServerLevel level, LevelChunk chunk) {
        for (long packed : chunk.getData(ForestFloor.AUTUMN_PUMPKINS)) {
            BlockPos pos = BlockPos.of(packed);
            if (level.getBlockState(pos).is(Blocks.PUMPKIN)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
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
