package com.terrainlab.event;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.world.entity.EntityTypes;

/**
 * Spawn placement rules: blocks daytime phantom spawns and enforces one living guide trader
 * of each type per world.
 */
public class ModSpawnRuleHandler {

    private static final boolean DEBUG_LOGGING = false;

    @SubscribeEvent
    public static void onCheckSpawn(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() != EntityTypes.PHANTOM) return;

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
