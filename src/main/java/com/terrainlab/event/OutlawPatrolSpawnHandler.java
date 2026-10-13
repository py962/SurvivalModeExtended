package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.TerrainLabStructureData;
import com.terrainlab.entity.OutlawRaiderEntity;
import com.terrainlab.registry.ModEntities;
import com.terrainlab.util.SafeSpotFinder;
import com.terrainlab.worldgen.BuriedHideoutPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.List;
import net.minecraft.world.entity.EntityTypes;

/**
 * Spawns a mounted outlaw patrol once per generated outlaws_hideout: six saddled, untamed
 * horses ridden by {@link OutlawRaiderEntity}, placed evenly around a loop 10-30 blocks
 * outside the hideout, which the riders then circle. Two of them instead ride up and down the
 * hideout's front tunnel.
 *
 * <p>Done here rather than via structure spawn_overrides, which can only spawn mobs on foot
 * inside the structure's bounding box. Riders steer their horses (a ridden mob's navigation
 * drives its vehicle), so all movement comes from the rider's goals.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class OutlawPatrolSpawnHandler {

    private static final int RIDER_COUNT = 6;

    private static final int RIDER_SAFE_SEARCH_RADIUS = 6; // blocks

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        LevelChunk chunk = event.getChunk();
        ChunkPos chunkPos = chunk.getPos();

        // Biome sampled near the build height: badlands mesas are tall enough that a fixed
        // Y can land underground in a cave biome.
        BlockPos centre = chunkPos.getMiddleBlockPosition(serverLevel.getMaxY());
        var biome = serverLevel.getBiome(centre);
        if (!biome.is(Biomes.BADLANDS) && !biome.is(Biomes.ERODED_BADLANDS)
                && !biome.is(Biomes.WOODED_BADLANDS)) return;

        var structureManager = serverLevel.structureManager();
        var registry = serverLevel.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE);

        ResourceKey<Structure> outlawsHideoutKey = ResourceKey.create(
                Registries.STRUCTURE,
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "outlaws_hideout"));

        var outlawsHideoutOpt = registry.get(outlawsHideoutKey);
        if (outlawsHideoutOpt.isEmpty()) return;

        Structure outlawsHideout = outlawsHideoutOpt.get().value();

        List<StructureStart> starts = structureManager.startsForStructure(chunkPos.x(), chunkPos.z(), s -> s == outlawsHideout);
        if (starts.isEmpty()) return;

        StructureStart start = starts.get(0);
        // The loop circles the buried chamber, not the whole start (its tunnels run far out).
        BoundingBox box = start.getBoundingBox();
        List<BlockPos> tunnelRoute = List.of();
        for (var piece : start.getPieces()) {
            if (piece instanceof BuriedHideoutPieces.ChamberPiece) {
                box = piece.getBoundingBox();
            } else if (piece instanceof BuriedHideoutPieces.TunnelPiece tunnel && tunnel.isFront()) {
                tunnelRoute = tunnel.routePoints();
            }
        }
        BoundingBox loopBox = box;
        List<BlockPos> route = tunnelRoute;

        TerrainLabStructureData savedData = TerrainLabStructureData.get(serverLevel);
        BlockPos originPos = new BlockPos(box.minX(), box.minY(), box.minZ());

        if (savedData.isOutlawCampProcessed(originPos)) return;
        savedData.markOutlawCampProcessed(originPos);

        serverLevel.getServer().execute(() -> spawnPatrol(serverLevel, loopBox, route));
    }

    /** Riders that patrol the front tunnel instead of the loop. */
    private static final int TUNNEL_RIDERS = 2;

    private static void spawnPatrol(ServerLevel level, BoundingBox box, List<BlockPos> tunnelRoute) {
        int centreX = (box.minX() + box.maxX()) / 2;
        int centreZ = (box.minZ() + box.maxZ()) / 2;
        int halfX = (box.maxX() - box.minX()) / 2;
        int halfZ = (box.maxZ() - box.minZ()) / 2;
        BlockPos centre = new BlockPos(centreX, box.minY(), centreZ);
        RandomSource random = level.getRandom();

        float offset = random.nextFloat() * Mth.TWO_PI;
        int spawned = 0;

        for (int i = 0; i < RIDER_COUNT; i++) {
            // Evenly spaced round the loop, each at its own distance out.
            float angle = (offset + i * Mth.TWO_PI / RIDER_COUNT) % Mth.TWO_PI;
            int distance = OutlawRaiderEntity.CIRCLE_MIN_DISTANCE + random.nextInt(
                    OutlawRaiderEntity.CIRCLE_MAX_DISTANCE - OutlawRaiderEntity.CIRCLE_MIN_DISTANCE + 1);

            boolean tunnelRider = i < TUNNEL_RIDERS && tunnelRoute.size() >= 2;
            BlockPos pos;
            if (tunnelRider) {
                // Start just outside the tunnel mouth.
                BlockPos mouth = tunnelRoute.get(tunnelRoute.size() - 1);
                pos = SafeSpotFinder.findNearestSafeSpot(level, surfaceGuess(level, mouth.getX(), mouth.getZ()),
                        RIDER_SAFE_SEARCH_RADIUS);
            } else {
                int x = centreX + Math.round(Mth.cos(angle) * (halfX + distance));
                int z = centreZ + Math.round(Mth.sin(angle) * (halfZ + distance));
                pos = SafeSpotFinder.findNearestSafeSpot(level, surfaceGuess(level, x, z), RIDER_SAFE_SEARCH_RADIUS);
            }

            if (pos == null) {
                continue;
            }

            if (spawnRider(level, pos, level.getCurrentDifficultyAt(pos), random,
                    centre, halfX, halfZ, distance, angle, tunnelRider ? tunnelRoute : List.of())) {
                spawned++;
            }
        }

        // TerrainLab.LOGGER.info(
                // "Outlaw patrol: spawned {}/{} mounted riders circling hideout at {},{}",
                // spawned, RIDER_COUNT, centreX, centreZ);
    }

    private static BlockPos surfaceGuess(ServerLevel level, int x, int z) {
        int y = level.getChunk(new BlockPos(x, 0, z))
                .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    private static boolean spawnRider(ServerLevel level, BlockPos pos, DifficultyInstance difficulty,
                                       RandomSource random, BlockPos hideoutCentre, int halfX, int halfZ,
                                       int distance, float angle, List<BlockPos> tunnelRoute) {
        Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (horse == null) {
            TerrainLab.LOGGER.warn("EntityTypes.HORSE.create() returned null - skipping one outlaw rider");
            return false;
        }

        float yaw = random.nextFloat() * 360.0F;
        horse.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        horse.setYRot(yaw);
        horse.setYHeadRot(yaw);
        horse.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, null);

        horse.setPersistenceRequired();
        horse.setTamed(false);
        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));

        var riderType = ModEntities.OUTLAW_RAIDER.get();
        var rider = riderType.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (rider == null) {
            TerrainLab.LOGGER.warn(
                    "ModEntities.OUTLAW_RAIDER create() returned null - horse spawned riderless at {}", pos);
            level.addFreshEntity(horse);
            return false;
        }

        rider.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        rider.setYRot(yaw);
        rider.setYHeadRot(yaw);
        rider.finalizeSpawn(level, difficulty, EntitySpawnReason.MOB_SUMMONED, null);
        rider.setPersistenceRequired();
        rider.setHideout(hideoutCentre, halfX, halfZ, distance, angle);
        if (!tunnelRoute.isEmpty()) {
            rider.setTunnelRoute(tunnelRoute);
        }

        level.addFreshEntity(horse);
        level.addFreshEntity(rider);
        // Forced: a raider on a horse is not a vanilla-allowed pairing.
        rider.startRiding(horse, true, true);

        return true;
    }
}
