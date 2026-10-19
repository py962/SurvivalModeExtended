
package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/**
 * Fills open air below the configured water level in swamp and marsh biome
 * columns, continuing through underground biome boundaries until solid ground
 * is reached. Where the flood would be exposed sideways to air at a boundary with
 * a non-swamp biome, a natural stone/dirt ledge is placed instead, occasionally
 * with a waterfall lip spilling over it (see {@link #place}).
 */
public final class SwampFloodFeature implements Feature {

    private static final int SEA_LEVEL = 63;

    /** One in this many biome-boundary columns gets a waterfall lip instead of a full ledge. */
    private static final int WATERFALL_CHANCE = 8;

    private static final List<ResourceKey<Biome>> TARGET_BIOMES = List.of(
            Biomes.SWAMP,
            Biomes.MANGROVE_SWAMP,
            ResourceKey.create(
                    Registries.BIOME,
                    Identifier.fromNamespaceAndPath("terrainlab", "marsh")
            ),
            ResourceKey.create(
                    Registries.BIOME,
                    Identifier.fromNamespaceAndPath("terrainlab", "frozen_marsh")
            )
    );

    public static final MapCodec<SwampFloodFeature> CODEC = MapCodec.unit(SwampFloodFeature::new);

    public SwampFloodFeature() {
    }

    @Override
    public MapCodec<SwampFloodFeature> codec() {
        return CODEC;
    }

    /**
     * Floods each target-biome column from {@code SEA_LEVEL - 1} down to the first
     * solid block, exactly as before, with one addition at biome boundaries.
     *
     * <p>Biome-edge ledge: a cell we would fill is "exposed" when one of its four
     * horizontal neighbours is air in a column that is NOT a target biome (target
     * neighbours are skipped because they get flooded too, possibly later or by the
     * neighbouring chunk's pass). Without help, those cells become a vertical wall
     * of unticked water ending abruptly at the biome edge. Instead, exposed cells
     * are turned into a solid ledge using the local terrain block (see
     * {@link #pickLedgeState}), so the swamp water ends against a natural lip.
     *
     * <p>Waterfall: in roughly 1 of {@link #WATERFALL_CHANCE} boundary columns the
     * highest exposed cell is left as a water source and given a fluid tick, so
     * vanilla flow spills it over the ledge as a small waterfall that falls and
     * settles as flowing (non-source) water in the neighbouring biome. All other
     * boundary columns are walled right up to their highest exposed cell.
     *
     * <p>Bounds: writes happen only inside this chunk's 16x16 columns, as before.
     * Neighbouring columns (which may lie in adjacent chunks) are only read.
     */
    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ChunkPos chunkPos = ChunkPos.containing(origin);
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();

        // Target-biome flags for this chunk plus a one-column border (read only).
        boolean[][] target = new boolean[18][18];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ix = 0; ix < 18; ix++) {
            for (int iz = 0; iz < 18; iz++) {
                pos.set(minX + ix - 1, SEA_LEVEL - 1, minZ + iz - 1);
                target[ix][iz] = isTargetBiome(level.getBiome(pos));
            }
        }

        boolean placedAny = false;
        BlockPos.MutableBlockPos npos = new BlockPos.MutableBlockPos();
        int[] fillYs = new int[SEA_LEVEL - level.getMinY() + 1];
        boolean[] exposed = new boolean[fillYs.length];

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                if (!target[dx + 1][dz + 1]) {
                    continue;
                }
                int worldX = minX + dx;
                int worldZ = minZ + dz;

                // Pass 1: collect the air cells to fill (same rules as before).
                int count = 0;
                BlockState floor = null;
                for (int y = SEA_LEVEL - 1; y >= level.getMinY(); y--) {
                    pos.set(worldX, y, worldZ);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        fillYs[count++] = y;
                    } else if (!state.is(Blocks.WATER)) {
                        floor = state;
                        break;
                    }
                }
                if (count == 0) {
                    continue;
                }

                // Pass 2: find cells exposed sideways to air in a non-target biome.
                int topExposed = Integer.MIN_VALUE;
                for (int i = 0; i < count; i++) {
                    int y = fillYs[i];
                    exposed[i] = false;
                    for (int d = 0; d < 4; d++) {
                        int ox = d == 0 ? -1 : d == 1 ? 1 : 0;
                        int oz = d == 2 ? -1 : d == 3 ? 1 : 0;
                        if (target[dx + 1 + ox][dz + 1 + oz]) {
                            continue;
                        }
                        npos.set(worldX + ox, y, worldZ + oz);
                        if (level.getBlockState(npos).isAir()) {
                            exposed[i] = true;
                            break;
                        }
                    }
                    if (exposed[i] && y > topExposed) {
                        topExposed = y;
                    }
                }

                int lipY = Integer.MIN_VALUE;
                if (topExposed != Integer.MIN_VALUE && random.nextInt(WATERFALL_CHANCE) == 0) {
                    lipY = topExposed;
                }

                // Pass 3: write water, ledge blocks, and the optional waterfall lip.
                for (int i = 0; i < count; i++) {
                    int y = fillYs[i];
                    pos.set(worldX, y, worldZ);
                    if (exposed[i] && y != lipY) {
                        level.setBlock(pos, pickLedgeState(level, pos, floor), 2);
                    } else {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                        if (y == lipY) {
                            level.scheduleTick(pos, Fluids.WATER, 0);
                        }
                    }
                    placedAny = true;
                }
            }
        }

        return placedAny;
    }

    /**
     * Chooses a ledge block that matches the terrain around {@code pos}: the first
     * natural terrain block among the cell's horizontal neighbours and the block
     * below, then the column floor, falling back to deepslate below y=0 and stone
     * above. Surface dirt variants (grass, podzol, ...) become plain dirt since the
     * ledge sits under water/in caves; gravity blocks are never used.
     */
    private static BlockState pickLedgeState(WorldGenLevel level, BlockPos pos, BlockState floor) {
        BlockState found = null;
        for (BlockPos p : new BlockPos[]{pos.west(), pos.east(), pos.north(), pos.south(), pos.below()}) {
            BlockState s = normalizeTerrain(level.getBlockState(p));
            if (s != null) {
                found = s;
                break;
            }
        }
        if (found == null && floor != null) {
            found = normalizeTerrain(floor);
        }
        if (found != null) {
            return found;
        }
        return pos.getY() < 0 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private static BlockState normalizeTerrain(BlockState s) {
        if (s.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return s.getBlock().defaultBlockState();
        }
        if (s.is(Blocks.MUD) || s.is(Blocks.CLAY)) {
            return s.getBlock().defaultBlockState();
        }
        if (s.is(BlockTags.DIRT)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return null;
    }

    private boolean isTargetBiome(Holder<Biome> biome) {
        for (ResourceKey<Biome> key : TARGET_BIOMES) {
            if (biome.is(key)) {
                return true;
            }
        }
        return false;
    }
}
