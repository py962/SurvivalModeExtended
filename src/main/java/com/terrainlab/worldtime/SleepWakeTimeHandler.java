package com.terrainlab.worldtime;

import com.terrainlab.TerrainLab;
import com.terrainlab.season.SolarTime;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.ClockAdjustment;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

/**
 * Sleeping through the night wakes everyone at 4am (raw clock) instead of morning. Outside midsummer
 * it is still dark then, so mobs keep spawning until sunrise. Runs first so other sleep listeners
 * (furnace catch-up) see the shorter skip.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class SleepWakeTimeHandler {
    private SleepWakeTimeHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !SolarTime.warps(level)) return;
        long now = level.getOverworldClockTime();
        long target = Math.floorDiv(now, 24000L) * 24000L + SolarTime.BED_END;
        if (target > now) {
            event.setAdjustment(new ClockAdjustment.Absolute(target));
        }
    }
}
