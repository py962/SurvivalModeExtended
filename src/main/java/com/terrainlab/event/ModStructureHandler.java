package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.TerrainLabStructureData;
import com.terrainlab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.List;
import java.util.Random;

/**
 * Adds a hidden chamber holding an Ancient Codex next to each desert pyramid, built when a
 * desert chunk referencing the pyramid is first generated.
 */
public class ModStructureHandler {

    private static final ResourceKey<Structure> DESERT_PYRAMID_KEY = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.withDefaultNamespace("desert_pyramid"));

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        LevelChunk chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();

        // Cheap biome pre-filter before the structure lookup.
        BlockPos centre = chunkPos.getMiddleBlockPosition(64);
        var biome = serverLevel.getBiome(centre);

        if (!biome.is(Biomes.DESERT)) {
            return;
        }

        // Deferred one tick: structure starts are not always available when the chunk loads.
        serverLevel.getServer().execute(() -> checkForDesertPyramid(
                serverLevel,
                chunkPos
        ));
    }

    private static void checkForDesertPyramid(
            ServerLevel serverLevel,
            ChunkPos chunkPos) {
        var structureManager = serverLevel.structureManager();

        var registry = serverLevel.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE);

        var desertPyramidOpt = registry.get(DESERT_PYRAMID_KEY);

        if (desertPyramidOpt.isEmpty()) {
            TerrainLab.LOGGER.warn(
                    "[DESERT_TEMPLE] '{}' not found in structure registry",
                    DESERT_PYRAMID_KEY.identifier()
            );
            return;
        }

        Structure desertPyramid = desertPyramidOpt.get().value();

        List<StructureStart> starts = structureManager.startsForStructure(
                chunkPos,
                structure -> structure == desertPyramid
        );

        if (starts.isEmpty()) {
            return;
        }

        // TerrainLab.LOGGER.info(
                // "[DESERT_TEMPLE] Found {} desert pyramid start(s) in chunk {}",
                // starts.size(),
                // chunkPos
        // );

        for (StructureStart start : starts) {
            BoundingBox boundingBox = start.getBoundingBox();

            BlockPos originPos = new BlockPos(
                    boundingBox.minX(),
                    boundingBox.minY(),
                    boundingBox.minZ()
            );

            // TerrainLab.LOGGER.info(
                    // "[DESERT_TEMPLE] Pyramid found at origin {} with bounding box {}",
                    // originPos,
                    // boundingBox
            // );

            TerrainLabStructureData savedData =
                    TerrainLabStructureData.get(serverLevel);

            if (savedData.isTempleProcessed(originPos)) {
                // TerrainLab.LOGGER.info(
                        // "[DESERT_TEMPLE] Pyramid at {} has already been processed",
                        // originPos
                // );
                continue;
            }

            // Marked processed only on success, so a failed build is retried later.
            boolean success = processTemple(
                    serverLevel,
                    boundingBox,
                    originPos
            );

            if (success) {
                savedData.markTempleProcessed(originPos);

                // TerrainLab.LOGGER.info(
                        // "[DESERT_TEMPLE] Pyramid at {} successfully processed",
                        // originPos
                // );
            } else {
                TerrainLab.LOGGER.warn(
                        "[DESERT_TEMPLE] Pyramid at {} could not be processed after 4 directional attempts",
                        originPos
                );
            }
        }
    }

    private static boolean processTemple(
            ServerLevel level,
            BoundingBox box,
            BlockPos originPos) {

        int centreX = (box.minX() + box.maxX()) / 2;
        int centreZ = (box.minZ() + box.maxZ()) / 2;

        // TerrainLab.LOGGER.info(
                // "[DESERT_TEMPLE] Searching for pressure plate near centre ({}, {})",
                // centreX,
                // centreZ
        // );

        BlockPos pressurePlate = null;

        int scanMinY = box.minY() - 40;

        outer:
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = box.maxY(); y >= scanMinY; y--) {

                    BlockPos pos = new BlockPos(
                            centreX + dx,
                            y,
                            centreZ + dz
                    );

                    var state = level.getBlockState(pos);

                    if (state.is(Blocks.STONE_PRESSURE_PLATE)) {
                        pressurePlate = pos;
                        break outer;
                    }
                }
            }
        }

        if (pressurePlate == null) {
            TerrainLab.LOGGER.warn(
                    "[DESERT_TEMPLE] Could not find pressure plate in desert pyramid at {}",
                    originPos
            );
            return false;
        }

        // TerrainLab.LOGGER.info(
                // "[DESERT_TEMPLE] Pressure plate found at {}",
                // pressurePlate
        // );

        Random rng = new Random(
                level.getSeed()
                        ^ originPos.getX() * 341873128712L
                        ^ originPos.getZ() * 132897987541L
        );

        Direction[] directions = {
                Direction.NORTH,
                Direction.SOUTH,
                Direction.EAST,
                Direction.WEST
        };

        int startIndex = rng.nextInt(4);

        // TerrainLab.LOGGER.info(
                // "[DESERT_TEMPLE] Random directional starting index = {} ({})",
                // startIndex,
                // directions[startIndex]
        // );

        // Try all four cardinal directions, starting from a seeded random one.
        for (int attempt = 0; attempt < 4; attempt++) {

            Direction dir = directions[
                    (startIndex + attempt) % directions.length
                    ];

            // TerrainLab.LOGGER.info(
                    // "[DESERT_TEMPLE] Attempt {}/4: trying direction {}",
                    // attempt + 1,
                    // dir
            // );

            if (isChamberSiteSolid(level, pressurePlate, dir)
                    && buildPassageAndChamber(
                    level,
                    pressurePlate,
                    dir)) {

                // TerrainLab.LOGGER.info(
                        // "[DESERT_TEMPLE] Direction {} succeeded on attempt {}/4",
                        // dir,
                        // attempt + 1
                // );

                return true;
            }

            // TerrainLab.LOGGER.info(
                    // "[DESERT_TEMPLE] Direction {} failed on attempt {}/4",
                    // dir,
                    // attempt + 1
            // );
        }

        // No direction has undisturbed ground; build in the seeded direction anyway.
        TerrainLab.LOGGER.warn(
                "[DESERT_TEMPLE] No solid site around pyramid at {}; using {} regardless",
                originPos,
                directions[startIndex]
        );

        return buildPassageAndChamber(level, pressurePlate, directions[startIndex]);
    }

    /**
     * True when the chamber's whole footprint, walls included, is solid ground: no air, fluid
     * or block entities, so carving it cannot break into the pyramid, a cave or water.
     */
    private static boolean isChamberSiteSolid(ServerLevel level, BlockPos pressurePlate, Direction dir) {
        BlockPos chamberCentre = pressurePlate.below().relative(dir, 3 + 7);
        Direction perpendicular = dir.getClockWise();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 3; y++) {
                    BlockPos p = chamberCentre.relative(perpendicular, x).relative(dir, z).above(y);
                    var state = level.getBlockState(p);
                    if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean buildPassageAndChamber(
            ServerLevel level,
            BlockPos pressurePlate,
            Direction dir) {

        BlockPos floorLevel = pressurePlate.below();
        BlockPos passageStart = floorLevel.relative(dir, 3);

        // TerrainLab.LOGGER.info(
                // "[DESERT_TEMPLE] Building passage from {} in direction {}",
                // passageStart,
                // dir
        // );

        for (int i = 0; i < 5; i++) {

            BlockPos base = passageStart.relative(dir, i);

            level.setBlock(
                    base,
                    Blocks.AIR.defaultBlockState(),
                    3
            );

            level.setBlock(
                    base.above(),
                    Blocks.AIR.defaultBlockState(),
                    3
            );
        }

        // The chamber begins beyond the passage.
        BlockPos chamberCentre = passageStart.relative(dir, 7);

        Direction perpendicular = dir.getClockWise();

        // TerrainLab.LOGGER.info(
                // "[DESERT_TEMPLE] Chamber centre for direction {} = {}",
                // dir,
                // chamberCentre
        // );

        // Clear the 5x3x5 chamber interior.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 0; y <= 2; y++) {

                    BlockPos p = chamberCentre
                            .relative(perpendicular, x)
                            .relative(dir, z)
                            .above(y);

                    level.setBlock(
                            p,
                            Blocks.AIR.defaultBlockState(),
                            3
                    );
                }
            }
        }

        // Floor, ceiling and walls.
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {

                BlockPos floor = chamberCentre
                        .relative(perpendicular, x)
                        .relative(dir, z)
                        .below();

                level.setBlock(
                        floor,
                        Blocks.SANDSTONE.defaultBlockState(),
                        3
                );

                BlockPos ceiling = chamberCentre
                        .relative(perpendicular, x)
                        .relative(dir, z)
                        .above(3);

                level.setBlock(
                        ceiling,
                        Blocks.SANDSTONE.defaultBlockState(),
                        3
                );

                if (Math.abs(x) == 2 || Math.abs(z) == 2) {

                    for (int y = 0; y <= 2; y++) {

                        BlockPos wall = chamberCentre
                                .relative(perpendicular, x)
                                .relative(dir, z)
                                .above(y);

                        boolean isCorner =
                                Math.abs(x) == 2
                                        && Math.abs(z) == 2;

                        level.setBlock(
                                wall,
                                isCorner
                                        ? Blocks.CHISELED_SANDSTONE.defaultBlockState()
                                        : Blocks.SANDSTONE.defaultBlockState(),
                                3
                        );
                    }
                }
            }
        }

        // Chiselled cross in the floor centre.
        level.setBlock(
                chamberCentre.below(),
                Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                3
        );

        for (Direction d : new Direction[]{
                Direction.NORTH,
                Direction.SOUTH,
                Direction.EAST,
                Direction.WEST}) {

            level.setBlock(
                    chamberCentre.below().relative(d),
                    Blocks.CHISELED_SANDSTONE.defaultBlockState(),
                    3
            );
        }

        level.setBlock(
                chamberCentre,
                ModBlocks.ANCIENT_CODEX.get().defaultBlockState(),
                3
        );

        level.setBlock(
                chamberCentre.above().above().above(),
                Blocks.LANTERN.defaultBlockState(),
                3
        );

        for (BlockPos candlePos : new BlockPos[]{
                chamberCentre.north().west(),
                chamberCentre.north().east(),
                chamberCentre.south().west(),
                chamberCentre.south().east()
        }) {

            level.setBlock(
                    candlePos,
                    Blocks.CANDLE.defaultBlockState()
                            .setValue(CandleBlock.LIT, true),
                    3
            );
        }

        // Success means the Codex block is actually present.
        boolean success = level.getBlockState(chamberCentre)
                .is(ModBlocks.ANCIENT_CODEX.get());

        if (success) {
            // TerrainLab.LOGGER.info(
                    // "[DESERT_TEMPLE] Verified Ancient Codex at {} - build succeeded",
                    // chamberCentre
            // );
        } else {
            TerrainLab.LOGGER.warn(
                    "[DESERT_TEMPLE] Ancient Codex missing at {} - build failed",
                    chamberCentre
            );
        }

        return success;
    }
}
