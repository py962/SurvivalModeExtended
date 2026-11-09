package com.terrainlab.worldgen;

import com.mojang.serialization.MapCodec;
import com.terrainlab.TerrainLab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Small cluster of suspicious sand buried 1-3 blocks under the surface, brushing out lapis.
 * Only sand with a supporting block underneath is replaced, so nothing falls.
 */
public final class SuspiciousSandPatchFeature implements Feature {

    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "archaeology/desert_lapis"));

    private static final int MIN_BLOCKS = 2;
    private static final int MAX_BLOCKS = 4;
    private static final int SPREAD = 1;
    private static final int MIN_DEPTH = 1;
    private static final int MAX_DEPTH = 3;

    public static final MapCodec<SuspiciousSandPatchFeature> CODEC = MapCodec.unit(SuspiciousSandPatchFeature::new);

    public SuspiciousSandPatchFeature() {
    }

    @Override
    public MapCodec<SuspiciousSandPatchFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        int blocks = MIN_BLOCKS + random.nextInt(MAX_BLOCKS - MIN_BLOCKS + 1);
        boolean placedAny = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < blocks; i++) {
            int x = origin.getX() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
            int z = origin.getZ() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
            int depth = MIN_DEPTH + random.nextInt(MAX_DEPTH - MIN_DEPTH + 1);
            // Heightmap gives the first open block; the top solid block is one below it.
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1 - depth;
            pos.set(x, y, z);

            if (!level.getBlockState(pos).is(Blocks.SAND)) continue;
            if (FallingBlock.isFree(level.getBlockState(pos.below()))) continue;

            level.setBlock(pos, Blocks.SUSPICIOUS_SAND.defaultBlockState(), 2);
            BlockPos placed = pos.immutable();
            level.getBlockEntity(placed, BlockEntityTypes.BRUSHABLE_BLOCK)
                    .ifPresent(be -> be.setLootTable(LOOT, random.nextLong()));
            placedAny = true;
        }
        return placedAny;
    }
}
