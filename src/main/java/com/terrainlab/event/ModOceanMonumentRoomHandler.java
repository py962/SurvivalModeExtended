package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.TerrainLabStructureData;
import com.terrainlab.entity.OversizedMapFrameEntity;
import com.terrainlab.registry.ModEntities;
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
import net.minecraft.world.level.block.WallTorchBlock;

import java.util.List;
import java.util.Set;

/**
 * Hidden chest room and map room under each Ocean Monument, placed below the seafloor found by a
 * column scan (the bounding box stops at the pillar tops and the heightmap reads the monument).
 */
public class ModOceanMonumentRoomHandler {

    private static final ResourceKey<Structure> MONUMENT_KEY = ResourceKey.create(
            Registries.STRUCTURE,
            Identifier.withDefaultNamespace("monument"));

    public static final ResourceKey<LootTable> LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "chests/hidden_monument_room"));

    /** The Sunken Chart (Trial Chambers explorer map), put in the map room's frame. */
    public static final ResourceKey<LootTable> MAP_LOOT_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "chests/hidden_monument_map"));

    /** Relative y of both rooms' roofs (floors at 0, interiors 5 high). */
    private static final int ROOM_TOP = 6;

    private static final BlockState SHELL = Blocks.TUFF_BRICKS.defaultBlockState();
    private static final BlockState FLOOR = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
    private static final BlockState TRIM = Blocks.DARK_PRISMARINE.defaultBlockState();

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

        List<StructureStart> starts = structureManager.startsForStructure(
                chunkPos, s -> s == monument);
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
        BlockState shell = SHELL;

        // Shells first so caves or water in the way do not matter.
        fill(level, origin, shell, 0, 0, 0, 8, ROOM_TOP, 8);
        fill(level, origin, shell, 9, 0, 3, 11, 3, 5);
        fill(level, origin, shell, 12, 0, 0, 18, ROOM_TOP, 8);

        BlockState air = Blocks.AIR.defaultBlockState();
        fill(level, origin, air, 1, 1, 1, 7, ROOM_TOP - 1, 7);   // chest room 7x7, 5 high
        fill(level, origin, air, 8, 1, 4, 12, 2, 4);             // corridor 1 wide, 2 high, through both walls
        fill(level, origin, air, 13, 1, 1, 17, ROOM_TOP - 1, 7); // map room 5 long, 7 wide, 5 high

        fill(level, origin, FLOOR, 1, 0, 1, 7, 0, 7);
        fill(level, origin, FLOOR, 8, 0, 4, 12, 0, 4);
        fill(level, origin, FLOOR, 13, 0, 1, 17, 0, 7);
        for (int wallX : new int[] {8, 12}) {
            fill(level, origin, TRIM, wallX, 1, 3, wallX, 3, 3);
            fill(level, origin, TRIM, wallX, 1, 5, wallX, 3, 5);
            level.setBlock(origin.offset(wallX, 3, 4), TRIM, 3);
        }

        BlockState torchWest = Blocks.COPPER_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST);
        BlockState torchEast = Blocks.COPPER_WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST);
        level.setBlock(origin.offset(7, 2, 3), torchWest, 3);
        level.setBlock(origin.offset(7, 2, 5), torchWest, 3);
        level.setBlock(origin.offset(13, 2, 3), torchEast, 3);
        level.setBlock(origin.offset(13, 2, 5), torchEast, 3);

        BlockState lantern = Blocks.SEA_LANTERN.defaultBlockState();
        level.setBlock(origin.offset(4, 2, 0), lantern, 3);
        level.setBlock(origin.offset(4, 2, 8), lantern, 3);
        level.setBlock(origin.offset(0, 2, 4), lantern, 3);
        level.setBlock(origin.offset(15, 2, 0), lantern, 3);
        level.setBlock(origin.offset(15, 2, 8), lantern, 3);

        BlockPos chestPos = origin.offset(4, 1, 4);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST), 3);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chestEntity) {
            chestEntity.setLootTable(LOOT_TABLE);
            chestEntity.setLootTableSeed(level.getRandom().nextLong());
        } else {
            TerrainLab.LOGGER.warn("[HIDDEN_MONUMENT_ROOM] chest block entity missing at {}", chestPos);
        }

        // Filled later by JungleTempleMapHandler; explorer maps can't be made during generation.
        fill(level, origin, TRIM, 18, 1, 2, 18, 5, 6);
        BlockPos framePos = origin.offset(17, 3, 4); // centre of the frame; covers z 3..5, y 2..4
        OversizedMapFrameEntity frame = new OversizedMapFrameEntity(ModEntities.EXTRA_LARGE_MAP_FRAME.get(),
                level, framePos, Direction.WEST);
        frame.getPersistentData().putBoolean(JungleTempleMapHandler.MAP_TAG, true);
        frame.getPersistentData().putString(JungleTempleMapHandler.MAP_LOOT_TAG, MAP_LOOT_TABLE.identifier().toString());
        level.addFreshEntity(frame);
    }

    private static void fill(ServerLevel level, BlockPos origin, BlockState state,
            int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    level.setBlock(origin.offset(x, y, z), state, 3);
                }
            }
        }
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

        // Lower of the two readings, so the map room doesn't break through the seafloor.
        int floorY = Math.min(
                findPillarFloorY(level, centreX, centreZ, monumentBox.minY()),
                findPillarFloorY(level, centreX + 15, centreZ, monumentBox.minY()));

        int originY = floorY - 1 - ROOM_TOP;
        return new BlockPos(centreX - 4, originY, centreZ - 4);
    }
}
