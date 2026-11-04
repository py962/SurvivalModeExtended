package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.AbstractTrailTraderEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Drops a level's guide (Trail Trader) runtime registries when it unloads, so closed worlds are not kept in memory. */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class TrailTraderLevelCleanup {
    private TrailTraderLevelCleanup() {}

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            AbstractTrailTraderEntity.forgetLevel(level);
        }
    }
}
