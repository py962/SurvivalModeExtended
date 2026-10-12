package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Moves the initial Overworld spawn to the nearest temperate biome (plains, forests,
 * cherry grove) found on an expanding ring search around the origin.
 */
public class ModSpawnHandler {

    private static final int SEARCH_RADIUS = 5000; // blocks
    private static final int STEP = 64; // blocks between biome samples

    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        LevelAccessor level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;

        ServerLevelData settings = event.getSettings();

        BlockPos bestPos = findTemperateSpawn(serverLevel);
        if (bestPos == null) {
            TerrainLab.LOGGER.warn("Could not find temperate spawn position, using vanilla");
            return;
        }

        int y = serverLevel.getChunk(bestPos).getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                bestPos.getX(), bestPos.getZ()) + 1;

        settings.setSpawn(LevelData.RespawnData.of(
                serverLevel.dimension(),
                new BlockPos(bestPos.getX(), y, bestPos.getZ()),
                0.0F, 0.0F));

        serverLevel.setDayTime(1400L);

        // Cancelling skips vanilla's bonus-chest step, so place it here when the option is on.
        if (serverLevel.getServer().getWorldData().worldGenOptions().generateBonusChest()) {
            BlockPos spawn = settings.getRespawnData().pos();
            serverLevel.registryAccess()
                    .lookup(Registries.CONFIGURED_FEATURE)
                    .flatMap(features -> features.get(MiscOverworldFeatures.BONUS_CHEST))
                    .ifPresent(feature -> feature.value().place(serverLevel,
                            serverLevel.getChunkSource().getGenerator(), serverLevel.random, spawn));
        }

        event.setCanceled(true);
        TerrainLab.LOGGER.info("Set spawn to temperate position: {}", bestPos);
    }

    private static BlockPos findTemperateSpawn(ServerLevel level) {
        for (int radius = 0; radius < SEARCH_RADIUS; radius += STEP) {
            for (int x = -radius; x <= radius; x += STEP) {
                for (int z = -radius; z <= radius; z += STEP) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;

                    BlockPos pos = new BlockPos(x, 64, z);
                    var biome = level.getBiome(pos);

                    if (biome.is(Biomes.PLAINS)
                            || biome.is(Biomes.FOREST)
                            || biome.is(Biomes.FLOWER_FOREST)
                            || biome.is(Biomes.BIRCH_FOREST)
                            || biome.is(Biomes.OLD_GROWTH_BIRCH_FOREST)
                            || biome.is(Biomes.SUNFLOWER_PLAINS)
                            || biome.is(Biomes.CHERRY_GROVE)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
