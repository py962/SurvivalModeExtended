package com.terrainlab.worldgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.slf4j.Logger;

import java.util.List;

/**
 * Floods inland_river biome columns with water up to their banded sea level (+2), and punches water gaps
 * through solid blocks along sea-level band boundaries on a regular lattice. Currently disabled via
 * {@code ENABLED}.
 */
public class InlandRiverFloodFeature extends Feature<NoneFeatureConfiguration> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/InlandRiverFlood]";

    // Guards the varargs logging calls, which allocate even when the log level is disabled.
    private static final boolean VERBOSE_LOGGING = false;

    private static final List<ResourceKey<Biome>> TARGET_BIOMES = List.of(
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("terrainlab", "inland_river"))
    );

    private static final int GAP_SPACING = 12; // blocks between gap-lattice repeats
    private static final int GAP_WIDTH = 8;    // blocks

    /** Kill switch; when re-enabling, also restore the feature in inland_river.json's feature list. */
    private static final boolean ENABLED = false;

    public InlandRiverFloodFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!ENABLED) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        ChunkPos chunkPos = new ChunkPos(origin);
        ChunkGenerator generator = context.chunkGenerator();

        // if (VERBOSE_LOGGING) LOGGER.trace("{} place() called for chunk {}", LOG_PREFIX, chunkPos);

        RandomState randomState = resolveRandomState(generator);
        if (randomState == null) {
            // if (VERBOSE_LOGGING) LOGGER.debug("{} chunk {} - could not resolve RandomState (not yet cached, or generator not banded-aware) - no-op",
            //         LOG_PREFIX, chunkPos);
            return false;
        }

        DensityFunction continentalness = randomState.router().continents();
        DensityFunction erosion = randomState.router().erosion();

        boolean placedAny = false;
        int blocksPlaced = 0;
        int columnsSkippedNotTargetBiome = 0;
        int gapColumnsChecked = 0;
        int gapBoundaryColumnsFound = 0;
        int gapBlocksForced = 0;
        int gapBlocksAlreadyWater = 0;
        int gapBlocksSkippedNotSolid = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int worldX = chunkPos.getMinBlockX() + dx;
                int worldZ = chunkPos.getMinBlockZ() + dz;

                int bandedSeaLevel = bandedLevelAt(continentalness, erosion, worldX, worldZ);

                if (isInGap(worldX, worldZ)) {
                    gapColumnsChecked++;

                    int levelEast = bandedLevelAt(continentalness, erosion, worldX + 1, worldZ);
                    int levelSouth = bandedLevelAt(continentalness, erosion, worldX, worldZ + 1);

                    boolean isBoundary = (bandedSeaLevel != levelEast) || (bandedSeaLevel != levelSouth);
                    if (isBoundary) {
                        gapBoundaryColumnsFound++;

                        int lower = Math.min(bandedSeaLevel, Math.min(levelEast, levelSouth));
                        int upper = Math.max(bandedSeaLevel, Math.max(levelEast, levelSouth));

                        // if (VERBOSE_LOGGING) LOGGER.trace("{} gap boundary at ({}, {}): band=[{},{}] (here={}, east={}, south={})",
                        //         LOG_PREFIX, worldX, worldZ, lower, upper, bandedSeaLevel, levelEast, levelSouth);

                        for (int y = lower; y <= upper; y++) {
                            pos.set(worldX, y, worldZ);
                            BlockState existing = level.getBlockState(pos);
                            if (existing.is(Blocks.WATER)) {
                                gapBlocksAlreadyWater++;
                            } else if (existing.blocksMotion()) {
                                // Only solid blocks: the gaps are for underground band walls, not open air.
                                level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                                gapBlocksForced++;
                                placedAny = true;
                                // if (VERBOSE_LOGGING) LOGGER.trace("{} forced gap water at ({}, {}, {})", LOG_PREFIX, worldX, y, worldZ);
                            } else {
                                gapBlocksSkippedNotSolid++;
                            }
                        }
                    }
                }

                int floodStartY = bandedSeaLevel + 2;
                boolean columnTouchedTargetBiome = false;

                for (int y = floodStartY; y >= level.getMinY(); y--) {
                    pos.set(worldX, y, worldZ);

                    Holder<Biome> biome = level.getBiome(pos);
                    if (!isTargetBiome(biome)) {
                        break;
                    }
                    columnTouchedTargetBiome = true;

                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
                        placedAny = true;
                        blocksPlaced++;
                    } else {
                        break;
                    }
                }

                if (!columnTouchedTargetBiome) {
                    columnsSkippedNotTargetBiome++;
                }
            }
        }

        if (placedAny) {
            // if (VERBOSE_LOGGING) LOGGER.debug("{} chunk {} - flood: placed {} blocks; gaps: checked {} gap-lattice columns, found {} boundary columns, forced {} blocks ({} already water, {} skipped as not solid)",
            //         LOG_PREFIX, chunkPos, blocksPlaced, gapColumnsChecked, gapBoundaryColumnsFound, gapBlocksForced, gapBlocksAlreadyWater, gapBlocksSkippedNotSolid);
        } else {
            // if (VERBOSE_LOGGING) LOGGER.trace("{} chunk {} - no blocks placed ({} of 256 columns were not target biome; {} gap-lattice columns checked, none were boundaries)",
            //         LOG_PREFIX, chunkPos, columnsSkippedNotTargetBiome, gapColumnsChecked);
        }

        return placedAny;
    }

    /** Banded sea level at a column, from raw continentalness and the continentalness-minus-erosion score. */
    private static int bandedLevelAt(DensityFunction continentalness, DensityFunction erosion, int x, int z) {
        DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(x, 0, z);
        double continentalnessSample = continentalness.compute(context);
        double erosionSample = erosion.compute(context);
        double combinedScore = continentalnessSample - erosionSample;
        return SeaLevelBandingService.bandedLevel(continentalnessSample, combinedScore);
    }

    private static boolean isInGap(int x, int z) {
        boolean gapX = Math.floorMod(x, GAP_SPACING) < GAP_WIDTH;
        boolean gapZ = Math.floorMod(z, GAP_SPACING) < GAP_WIDTH;
        return gapX || gapZ;
    }

    private RandomState resolveRandomState(ChunkGenerator generator) {
        if (generator instanceof NoiseBasedChunkGenerator && generator instanceof NoiseBasedChunkGeneratorRandomStateProvider provider) {
            RandomState randomState = provider.terrainlab$getRandomState();
            if (randomState != null) {
                return randomState;
            }
            // if (VERBOSE_LOGGING) LOGGER.debug("{} resolveRandomState: provider present but RandomState was null", LOG_PREFIX);
        } else {
            // if (VERBOSE_LOGGING) LOGGER.debug("{} resolveRandomState: generator is not a banded-aware NoiseBasedChunkGenerator (actual type: {})",
            //         LOG_PREFIX, generator.getClass().getName());
        }
        return null;
    }

    private boolean isTargetBiome(Holder<Biome> biome) {
        for (ResourceKey<Biome> key : TARGET_BIOMES) {
            if (biome.is(key)) return true;
        }
        return false;
    }
}
