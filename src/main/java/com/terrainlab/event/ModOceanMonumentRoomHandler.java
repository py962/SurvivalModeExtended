package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.TerrainLabStructureData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;

import java.util.List;
import java.util.Set;

/**
 * Builds a hidden loot room beneath each Ocean Monument when the monument's chunks are
 * first generated. The room sits just under the seafloor found by scanning down the
 * monument's centre column: the structure bounding box ends at the top of the support
 * pillars, and the ocean-floor heightmap reads the monument itself.
 */
public class ModOceanMonumentRoomHandler {

    private static final ResourceKey<Structure> MONUMENT_KEY = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.withDefaultNamespace("monument"));

    public static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "chests/hidden_monument_room"));

    private static final int WIDTH = 5;
    private static final int HEIGHT = 4;
    private static final int DEPTH = 5;

    // Monument materials skipped by the pillar scan, along with fluids and air.
    private static final Set<Block> MONUMENT_MATERIALS = Set.of(
            Blocks.PRISMARINE,
            Blocks.PRISMARINE_BRICKS,
            Blocks.DARK_PRISMARINE,
            Blocks.SEA_LANTERN
    );

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        LevelChunk chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();

        var structureManager = serverLevel.structureManager();
        var registry = serverLevel.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        var monumentOpt = registry.get(MONUMENT_KEY);
        if (monumentOpt.isEmpty()) {
            TerrainLab.LOGGER.warn("[HIDDEN_MONUMENT_ROOM] '{}' not found in structure registry - check the resource ID", MONUMENT_KEY.identifier());
            return;
        }
        Structure monument = monumentOpt.get().value();

        List<StructureStart> starts = structureManager.startsForStructure(chunkPos.x(), chunkPos.z(), s -> s == monument);
        if (starts.isEmpty()) return;

        // TerrainLab.LOGGER.info("[HIDDEN_MONUMENT_ROOM] monument start found at chunk {} - {} start(s)", chunkPos, starts.size());

        StructureStart start = starts.get(0);
        BoundingBox box = start.getBoundingBox();

        TerrainLabStructureData savedData = TerrainLabStructureData.get(serverLevel);
        BlockPos originKey = new BlockPos(box.minX(), box.minY(), box.minZ());

        if (savedData.isMonumentRoomProcessed(originKey)) {
            // TerrainLab.LOGGER.info("[HIDDEN_MONUMENT_ROOM] origin {} already processed, skipping", originKey);
            return;
        }
        savedData.markMonumentRoomProcessed(originKey);

        // TerrainLab.LOGGER.info("[HIDDEN_MONUMENT_ROOM] scheduling build for monument at origin={}", originKey);

        serverLevel.getServer().execute(() ->
                buildHiddenRoom(serverLevel, box, originKey));
    }

    private static void buildHiddenRoom(ServerLevel level, BoundingBox monumentBox, BlockPos originKey) {
        BlockPos origin = findRoomOrigin(level, monumentBox);

        // TerrainLab.LOGGER.info("[HIDDEN_MONUMENT_ROOM] building at origin={} (monument origin={})", origin, originKey);

        // Solid shell, written unconditionally so caves, water or void at the target don't matter.
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < DEPTH; z++) {
                    boolean isShell = x == 0 || x == WIDTH - 1
                            || y == 0 || y == HEIGHT - 1
                            || z == 0 || z == DEPTH - 1;
                    if (isShell) {
                        level.setBlock(origin.offset(x, y, z),
                                Blocks.COPPER_BLOCK.weathering().weathered().defaultBlockState(), 3);
                    }
                }
            }
        }

        for (int x = 1; x < WIDTH - 1; x++) {
            for (int y = 1; y < HEIGHT - 1; y++) {
                for (int z = 1; z < DEPTH - 1; z++) {
                    level.setBlock(origin.offset(x, y, z),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        // Sea lanterns embedded in the middle of each side wall.
        level.setBlock(origin.offset(WIDTH / 2, 1, 0), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        level.setBlock(origin.offset(WIDTH / 2, 1, DEPTH - 1), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        level.setBlock(origin.offset(0, 1, DEPTH / 2), Blocks.SEA_LANTERN.defaultBlockState(), 3);
        level.setBlock(origin.offset(WIDTH - 1, 1, DEPTH / 2), Blocks.SEA_LANTERN.defaultBlockState(), 3);

        // Chest against one interior wall.
        BlockPos chestPos = origin.offset(WIDTH / 2, 1, 1);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 3);

        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chestEntity) {
            chestEntity.setLootTable(LOOT_TABLE);
            chestEntity.setLootTableSeed(level.getRandom().nextLong());
        } else {
            TerrainLab.LOGGER.warn("[HIDDEN_MONUMENT_ROOM] chest block entity missing at {}", chestPos);
        }

        // TerrainLab.LOGGER.info("[HIDDEN_MONUMENT_ROOM] build complete at {}", origin);
    }

    /**
     * Scans down from {@code startY}, skipping monument blocks, fluids and air, and returns
     * the first other block's Y (the seafloor under the pillar); falls back to {@code startY}.
     */
    private static int findPillarFloorY(ServerLevel level, int x, int z, int startY) {
        int minWorldY = level.getMinY();
        int y = startY;

        while (y > minWorldY) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            boolean isMonumentOrVoid = state.isAir()
                    || !state.getFluidState().isEmpty()
                    || MONUMENT_MATERIALS.contains(state.getBlock());

            if (!isMonumentOrVoid) {
                return y;
            }
            y--;
        }

        TerrainLab.LOGGER.warn(
                "[HIDDEN_MONUMENT_ROOM] pillar scan hit world bottom at ({},{}) without finding terrain - falling back to startY={}",
                x, z, startY);
        return startY;
    }

    private static BlockPos findRoomOrigin(ServerLevel level, BoundingBox monumentBox) {
        int centreX = (monumentBox.minX() + monumentBox.maxX()) / 2;
        int centreZ = (monumentBox.minZ() + monumentBox.maxZ()) / 2;

        int floorY = findPillarFloorY(level, centreX, centreZ, monumentBox.minY());

        // TerrainLab.LOGGER.info(
                // "[HIDDEN_MONUMENT_ROOM] pillar scan at ({},{}) found floor at y={} (pillar top was y={})",
                // centreX, centreZ, floorY, monumentBox.minY());

        int roomTopY = floorY - 1;
        int originY = roomTopY - (HEIGHT - 1);

        return new BlockPos(centreX - WIDTH / 2, originY, centreZ - DEPTH / 2);
    }
}
