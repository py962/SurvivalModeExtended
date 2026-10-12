package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.TerrainLabStructureData;
import com.terrainlab.registry.ModEntities;
import com.terrainlab.util.SafeSpotFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Spawns a one-time pirate garrison per generated pirates_cove: four pirates on the shore
 * sharing a roam anchor, and four in two boats just offshore (movement for the boats is
 * provided by {@link PirateBoatSteeringHandler}). Separate from the structure's
 * spawn_overrides, which only spawn mobs on foot inside its bounding box.
 * <p>
 * The garrison is spawned {@link #GARRISON_SPAWN_DELAY_TICKS} after detection so nearby
 * jigsaw placement can settle before terrain and water are sampled. At the same time the
 * cove's bounding box is swept to clear waterlogging that structure placement inherits from
 * the ocean it was carved out of (nothing in the template is meant to be waterlogged).
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class PiratePatrolSpawnHandler {

    private static final int LAND_PIRATE_COUNT = 4;
    private static final int BOAT_COUNT = 2;
    private static final int PIRATES_PER_BOAT = 2;

    /** Spawn annulus around the cove (blocks); first guesses, not measured against the final template. */
    private static final int SPAWN_MIN_RADIUS = 20;
    private static final int SPAWN_MAX_RADIUS = 40;

    /** Roam radius (blocks) around the land group's shared anchor. */
    private static final int ROAM_RADIUS = 48;

    /** Spawn spread (blocks) of the land pirates around the anchor. */
    private static final int GROUP_SPREAD = 6;

    private static final int ANCHOR_SAFE_SEARCH_RADIUS = 8;
    private static final int RIDER_SAFE_SEARCH_RADIUS = 4;
    private static final int MAX_ANCHOR_ATTEMPTS = 24;
    private static final int MAX_WATER_ATTEMPTS = 32;

    /** Delay before sampling terrain, letting nearby structure placement settle. */
    private static final int GARRISON_SPAWN_DELAY_TICKS = 60; // 3 seconds

    private record PendingGarrison(ServerLevel level, BoundingBox box, long fireAtTick) {}

    private static final List<PendingGarrison> PENDING_GARRISONS = new ArrayList<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        LevelChunk chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();

        // Biome sampled near the build height (a fixed low Y can hit a cave biome); must
        // match the biomes pirates_cove.json allows.
        BlockPos centre = chunkPos.getMiddleBlockPosition(serverLevel.getMaxY());
        var biome = serverLevel.getBiome(centre);
        if (!(biome.is(Biomes.STONY_SHORE)
                || biome.is(Biomes.OCEAN)
                || biome.is(Biomes.WARM_OCEAN)
                || biome.is(Biomes.COLD_OCEAN))) return;

        var structureManager = serverLevel.structureManager();
        var registry = serverLevel.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE);

        ResourceKey<Structure> piratesCoveKey = ResourceKey.create(
                Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "pirates_cove"));

        var piratesCoveOpt = registry.get(piratesCoveKey);
        if (piratesCoveOpt.isEmpty()) return;

        Structure piratesCove = piratesCoveOpt.get().value();

        List<StructureStart> starts = structureManager.startsForStructure(
                chunkPos, s -> s == piratesCove);
        if (starts.isEmpty()) return;

        StructureStart start = starts.get(0);
        BoundingBox box = start.getBoundingBox();

        TerrainLabStructureData savedData = TerrainLabStructureData.get(serverLevel);
        BlockPos originPos = new BlockPos(box.minX(), box.minY(), box.minZ());

        if (savedData.isPirateCoveProcessed(originPos)) return;
        savedData.markPirateCoveProcessed(originPos);

        serverLevel.getServer().execute(() -> PENDING_GARRISONS.add(
                new PendingGarrison(serverLevel, box, serverLevel.getGameTime() + GARRISON_SPAWN_DELAY_TICKS)));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (PENDING_GARRISONS.isEmpty()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        long now = level.getGameTime();
        Iterator<PendingGarrison> it = PENDING_GARRISONS.iterator();
        while (it.hasNext()) {
            PendingGarrison pending = it.next();
            if (pending.level() != level) continue;
            if (now < pending.fireAtTick()) continue;
            it.remove();
            fixAccidentalWaterlogging(level, pending.box());
            spawnGarrison(level, pending.box());
        }
    }

    /** Clears waterlogged=true that structure placement introduced inside the cove. */
    private static void fixAccidentalWaterlogging(ServerLevel level, BoundingBox box) {
        int fixed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.hasProperty(BlockStateProperties.WATERLOGGED)) continue;
                    if (!state.getValue(BlockStateProperties.WATERLOGGED)) continue;
                    level.setBlock(cursor, state.setValue(BlockStateProperties.WATERLOGGED, false), Block.UPDATE_ALL);
                    fixed++;
                }
            }
        }
        if (fixed > 0) {
            // TerrainLab.LOGGER.info("Pirates Cove: cleared accidental waterlogging on {} block(s)", fixed);
        }
    }

    private static void spawnGarrison(ServerLevel level, BoundingBox box) {
        int centreX = (box.minX() + box.maxX()) / 2;
        int centreZ = (box.minZ() + box.maxZ()) / 2;
        RandomSource random = level.getRandom();

        int landSpawned = spawnLandPirates(level, centreX, centreZ, random);
        int boatSpawned = spawnBoatPirates(level, centreX, centreZ, random);

        int boatPirateTarget = BOAT_COUNT * PIRATES_PER_BOAT;
        // TerrainLab.LOGGER.info(
                // "Pirates Cove garrison: spawned {}/{} on land, {}/{} in {} boats near {},{}",
                // landSpawned, LAND_PIRATE_COUNT, boatSpawned, boatPirateTarget, BOAT_COUNT, centreX, centreZ);
    }

    private static int spawnLandPirates(ServerLevel level, int centreX, int centreZ, RandomSource random) {
        BlockPos anchor = findLandAnchor(level, centreX, centreZ, random);
        if (anchor == null) {
            TerrainLab.LOGGER.warn(
                    "Could not find a safe shore spot for the pirate cove's land pirates near {},{}",
                    centreX, centreZ);
            return 0;
        }

        DifficultyInstance difficulty = level.getCurrentDifficultyAt(anchor);
        int spawned = 0;
        for (int i = 0; i < LAND_PIRATE_COUNT; i++) {
            BlockPos pos = findNearbySafeSpot(level, anchor, random);
            if (pos == null) pos = anchor;
            if (spawnLandPirate(level, pos, difficulty, anchor)) spawned++;
        }
        return spawned;
    }

    private static boolean spawnLandPirate(ServerLevel level, BlockPos pos, DifficultyInstance difficulty,
                                            BlockPos roamAnchor) {
        var pirateType = ModEntities.PIRATE_RAIDER.get();
        var pirate = pirateType.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (pirate == null) {
            TerrainLab.LOGGER.warn("ModEntities.PIRATE_RAIDER create() returned null - skipping one land pirate");
            return false;
        }

        float yaw = level.getRandom().nextFloat() * 360.0F;
        pirate.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        pirate.setYRot(yaw);
        pirate.setYHeadRot(yaw);
        pirate.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, null);
        pirate.setPersistenceRequired();

        pirate.setHomeTo(roamAnchor, ROAM_RADIUS);

        level.addFreshEntity(pirate);
        return true;
    }

    private static BlockPos findLandAnchor(ServerLevel level, int centreX, int centreZ, RandomSource random) {
        for (int attempt = 0; attempt < MAX_ANCHOR_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int radius = SPAWN_MIN_RADIUS + random.nextInt(SPAWN_MAX_RADIUS - SPAWN_MIN_RADIUS + 1);
            int x = centreX + (int) Math.round(Math.cos(angle) * radius);
            int z = centreZ + (int) Math.round(Math.sin(angle) * radius);

            BlockPos guess = surfaceGuess(level, x, z);
            BlockPos safe = SafeSpotFinder.findNearestSafeSpot(level, guess, ANCHOR_SAFE_SEARCH_RADIUS);
            if (safe != null) return safe;
        }
        return null;
    }

    private static BlockPos findNearbySafeSpot(ServerLevel level, BlockPos anchor, RandomSource random) {
        int dx = random.nextInt(GROUP_SPREAD * 2 + 1) - GROUP_SPREAD;
        int dz = random.nextInt(GROUP_SPREAD * 2 + 1) - GROUP_SPREAD;
        int x = anchor.getX() + dx;
        int z = anchor.getZ() + dz;

        BlockPos guess = surfaceGuess(level, x, z);
        return SafeSpotFinder.findNearestSafeSpot(level, guess, RIDER_SAFE_SEARCH_RADIUS);
    }

    private static BlockPos surfaceGuess(ServerLevel level, int x, int z) {
        int y = level.getChunk(new BlockPos(x, 0, z))
                .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static int spawnBoatPirates(ServerLevel level, int centreX, int centreZ, RandomSource random) {
        int spawned = 0;
        for (int i = 0; i < BOAT_COUNT; i++) {
            BlockPos waterPos = findWaterSpot(level, centreX, centreZ, random);
            if (waterPos == null) continue;
            spawned += spawnBoatWithPirates(level, waterPos, random);
        }
        int target = BOAT_COUNT * PIRATES_PER_BOAT;
        if (spawned < target) {
            TerrainLab.LOGGER.warn(
                    "Pirates Cove: only got {}/{} boat pirates aboard near {},{}",
                    spawned, target, centreX, centreZ);
        }
        return spawned;
    }

    /** Spawns one boat and up to PIRATES_PER_BOAT riders on it; returns how many riders actually boarded. */
    private static int spawnBoatWithPirates(ServerLevel level, BlockPos waterPos, RandomSource random) {
        Boat boat = EntityType.OAK_BOAT.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (boat == null) {
            TerrainLab.LOGGER.warn("EntityType.OAK_BOAT.create() returned null - skipping one boat");
            return 0;
        }

        float yaw = random.nextFloat() * 360.0F;
        boat.setPos(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5);
        boat.setYRot(yaw);
        level.addFreshEntity(boat);

        DifficultyInstance difficulty = level.getCurrentDifficultyAt(waterPos);
        int boarded = 0;
        for (int i = 0; i < PIRATES_PER_BOAT; i++) {
            var pirateType = ModEntities.PIRATE_RAIDER.get();
            var pirate = pirateType.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (pirate == null) {
                TerrainLab.LOGGER.warn("ModEntities.PIRATE_RAIDER create() returned null - one seat left empty at " + waterPos);
                continue;
            }

            pirate.setPos(waterPos.getX() + 0.5, waterPos.getY(), waterPos.getZ() + 0.5);
            pirate.setYRot(yaw);
            pirate.setYHeadRot(yaw);
            pirate.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, null);
            pirate.setPersistenceRequired();

            level.addFreshEntity(pirate);
            // Forced: a pirate in a boat is not a vanilla-allowed pairing. The first boarder
            // takes the bow seat, the second the stern.
            pirate.startRiding(boat, true, true);
            boarded++;
        }
        return boarded;
    }

    /** Searches an annulus around the cove for open water; returns the air block above its surface. */
    private static BlockPos findWaterSpot(ServerLevel level, int centreX, int centreZ, RandomSource random) {
        for (int attempt = 0; attempt < MAX_WATER_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int radius = SPAWN_MIN_RADIUS + random.nextInt(SPAWN_MAX_RADIUS - SPAWN_MIN_RADIUS + 1);
            int x = centreX + (int) Math.round(Math.cos(angle) * radius);
            int z = centreZ + (int) Math.round(Math.sin(angle) * radius);

            BlockPos surface = findWaterSurface(level, x, z);
            if (surface != null) return surface;
        }
        return null;
    }

    private static BlockPos findWaterSurface(ServerLevel level, int x, int z) {
        int floorY = level.getChunk(new BlockPos(x, 0, z))
                .getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, floorY + 1, z);
        if (level.getFluidState(cursor).isEmpty()) return null; // dry column

        int y = floorY + 1;
        int ceiling = floorY + 32; // cap for unexpectedly deep columns
        while (y < ceiling && !level.getFluidState(cursor.setY(y + 1)).isEmpty()) {
            y++;
        }
        // y is the topmost water block; the boat rests in the block above it.
        return new BlockPos(x, y + 1, z);
    }
}
