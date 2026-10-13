package com.terrainlab.item;

import com.terrainlab.TerrainLab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.material.MapColor;

/**
 * Generates the static biome overview for {@link AtlasItem}: a 128x128 canvas covering 8,192 blocks per
 * axis, sampled from the chunk generator on a 64x64 grid (2x2 pixels per sample) and coloured via
 * {@link BiomePalette}.
 */
public final class AtlasMapGenerator {

    /** Vanilla map image dimensions. */
    public static final int CANVAS = 128;

    /** World span of the map along each horizontal axis, in blocks. */
    public static final int COVERAGE_BLOCKS = 8192;

    /** Samples taken along each horizontal axis. */
    public static final int SAMPLE_RESOLUTION = 64;

    /** Map pixels per sample along each axis. */
    public static final int PIXELS_PER_SAMPLE = CANVAS / SAMPLE_RESOLUTION;

    /** World blocks per sample cell along each axis. */
    public static final int BLOCKS_PER_SAMPLE = COVERAGE_BLOCKS / SAMPLE_RESOLUTION;

    /** Surface Y at or above which a sample renders as snowy mountain regardless of biome. */
    private static final int MOUNTAIN_Y_THRESHOLD = 150;

    private AtlasMapGenerator() {}

    /**
     * Samples the world around {@code center} into a new, unregistered map data. The caller registers it
     * and stamps the MapId onto the stack on the server thread.
     */
    public static net.minecraft.world.level.saveddata.maps.MapItemSavedData generate(
            ServerLevel level,
            BlockPos center
    ) {
        net.minecraft.world.level.saveddata.maps.MapItemSavedData data =
                net.minecraft.world.level.saveddata.maps.MapItemSavedData.createFresh(
                        center.getX(),
                        center.getZ(),
                        (byte) 0,
                        false,
                        false,
                        level.dimension()
                );

        int originX = center.getX() - COVERAGE_BLOCKS / 2;
        int originZ = center.getZ() - COVERAGE_BLOCKS / 2;

        for (int sampleZ = 0; sampleZ < SAMPLE_RESOLUTION; sampleZ++) {
            for (int sampleX = 0; sampleX < SAMPLE_RESOLUTION; sampleX++) {

                int worldX = originX
                        + sampleX * BLOCKS_PER_SAMPLE
                        + BLOCKS_PER_SAMPLE / 2;

                int worldZ = originZ
                        + sampleZ * BLOCKS_PER_SAMPLE
                        + BLOCKS_PER_SAMPLE / 2;

                MapColor color = samplePixel(
                        level,
                        worldX,
                        worldZ,
                        sampleX,
                        sampleZ
                );

                int colorId = color.getPackedId(MapColor.Brightness.NORMAL);

                for (int dz = 0; dz < PIXELS_PER_SAMPLE; dz++) {
                    for (int dx = 0; dx < PIXELS_PER_SAMPLE; dx++) {

                        int pixelX = sampleX * PIXELS_PER_SAMPLE + dx;
                        int pixelZ = sampleZ * PIXELS_PER_SAMPLE + dz;

                        data.updateColor(
                                pixelX,
                                pixelZ,
                                (byte) colorId
                        );
                    }
                }
            }
        }

        return data;
    }

    /** Colour for one sample, read from the generator's height and biome source rather than loaded chunks. */
    private static MapColor samplePixel(
            ServerLevel level,
            int wx,
            int wz,
            int sampleX,
            int sampleZ
    ) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();

        int surfaceY = generator.getBaseHeight(
                wx,
                wz,
                Heightmap.Types.WORLD_SURFACE,
                level,
                randomState
        );

        // No blocks to inspect, so a surface at or below sea level counts as ocean.
        boolean isOcean = surfaceY <= level.getSeaLevel();

        if (isOcean) {

            return AtlasColors.OCEAN;
        }

        Holder<Biome> biome = generator.getBiomeSource().createUncachedResolver(randomState).getNoiseBiome(
                QuartPos.fromBlock(wx),
                QuartPos.fromBlock(surfaceY),
                QuartPos.fromBlock(wz)
        );

        Identifier biomeId = biome.unwrapKey()
                .map(key -> key.identifier())
                .orElse(null);

        if (surfaceY >= MOUNTAIN_Y_THRESHOLD) {

            return AtlasColors.SNOWY;
        }

        MapColor color = BiomePalette.classify(biome);

        return color;
    }

    /** Category name for diagnostic output. */
    private static String colourName
(MapColor color) {
        if (color == AtlasColors.OCEAN) {
            return "OCEAN";
        }
        if (color == AtlasColors.ARID) {
            return "ARID";
        }
        if (color == AtlasColors.ARID_WOODLAND) {
            return "ARID_WOODLAND";
        }
        if (color == AtlasColors.TROPICAL_WOODLAND) {
            return "TROPICAL_WOODLAND";
        }
        if (color == AtlasColors.PLAINS) {
            return "PLAINS";
        }
        if (color == AtlasColors.TEMPERATE_WOODLAND) {
            return "TEMPERATE_WOODLAND";
        }
        if (color == AtlasColors.WETLANDS) {
            return "WETLANDS";
        }
        if (color == AtlasColors.BOREAL_WOODLAND) {
            return "BOREAL_WOODLAND";
        }
        if (color == AtlasColors.SNOWY) {
            return "SNOWY";
        }
        if (color == AtlasColors.STONY) {
            return "STONY";
        }

        return "UNKNOWN";
    }
}
