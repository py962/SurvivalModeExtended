package com.terrainlab.event;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModEntities;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Spawn placement rules: phantom-only hostile spawns above y 200, blocks daytime phantom spawns and enforces one living guide trader
 * of each type per world.
 */
public class ModSpawnRuleHandler {

    private static final boolean DEBUG_LOGGING = false;

    /** Natural hostile spawns at or above this height are phantoms only. */
    private static final int PHANTOM_ONLY_MIN_Y = 200;

    private static final Weighted<MobSpawnSettings.SpawnerData> HIGH_ALTITUDE_PHANTOMS =
            new Weighted<>(new MobSpawnSettings.SpawnerData(EntityType.PHANTOM, 2, 4), 100);

    // Above y 200 in the Overworld the natural hostile spawn list (biome and structure entries) is
    // swapped for phantoms in packs of 2-4. Caps, spacing and the night-only rule below still apply.
    // Spawners, structures' own placed mobs and events don't go through this list.
    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER) return;
        if (event.getPos().getY() < PHANTOM_ONLY_MIN_Y) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;

        List<Weighted<MobSpawnSettings.SpawnerData>> current = List.copyOf(event.getSpawnerDataList());
        for (Weighted<MobSpawnSettings.SpawnerData> entry : current) {
            event.removeSpawnerData(entry);
        }
        event.addSpawnerData(HIGH_ALTITUDE_PHANTOMS);
    }

    @SubscribeEvent
    public static void onCheckSpawn(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() != EntityType.PHANTOM) return;

        ServerLevelAccessor level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        if (serverLevel.getSkyDarken() < 5) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    // One guide trader of each type (Ranger/Archeologist/Mariner/Priest) per world. Reads the
    // saved LIVING_TRAIL_TRADERS record via getLevel(), which works for both ServerLevel and
    // WorldGenRegion (worldgen spawns); a second gate behind
    // AbstractTrailTraderEntity#checkTrailTraderSpawnRules.
    @SubscribeEvent
    public static void onCheckUniqueTraderSpawn(MobSpawnEvent.SpawnPlacementCheck event) {
        EntityType<?> type = event.getEntityType();
        if (type != ModEntities.RANGER.get()
                && type != ModEntities.ARCHEOLOGIST.get()
                && type != ModEntities.MARINER.get()
                && type != ModEntities.PRIEST.get()) {
            return;
        }

        ServerLevel serverLevel = event.getLevel().getLevel();
        if (serverLevel.getData(ModAttachments.LIVING_TRAIL_TRADERS.get()).containsKey(type)) {
            com.terrainlab.debug.GuideSpawnTrace.record(type, com.terrainlab.debug.GuideSpawnTrace.Stage.WORLD_RECORD);
            if (DEBUG_LOGGING) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                        // "[TrailTraderSpawnDebug] type={} pos={} thread={} -> FAIL (world record: one already exists)",
                        // type, event.getPos(), Thread.currentThread().getName());
            }
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    /**
     * Creates the trader attachments on the server thread at level load, so later reads from
     * worldgen threads never race to create them.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getData(ModAttachments.LIVING_TRAIL_TRADERS.get());
            serverLevel.getData(ModAttachments.BONDED_TRAIL_TRADER_TYPE.get());
        }
    }
}
