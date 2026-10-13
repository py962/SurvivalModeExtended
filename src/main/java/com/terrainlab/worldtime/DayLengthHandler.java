package com.terrainlab.worldtime;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.season.SeasonalDayLength;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Seasonal day length by slowing the overworld clock: vanilla still advances dayTime every tick (so
 * advance_time and sleep skipping keep working), and this handler subtracts ticks to reach
 * SeasonalDayLength's target rate. A full cycle takes 48,000 ticks; the day half ranges from 12,000
 * (midwinter) to 36,000 (midsummer). The fractional shortfall is accumulated per level.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class DayLengthHandler {

    // ABANDONED - NEVER REINSTATE. Pushing the clock back a tick at a time broke the game severely.
    // Seasonal time of day is handled by SolarTime instead; this stays disabled for reference only.
    private static final boolean ENABLED = false;

    private static final Map<ServerLevel, Double> shortfallAccumulator = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!ENABLED) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;

        long dayTime = level.getOverworldClockTime();
        long dayTimeMod = ((dayTime % 24_000L) + 24_000L) % 24_000L; // defensive against negative dayTime
        long seasonalOffset = SeasonSavedData.get(level).getSeasonalOffset();
        long yearTick = SeasonManager.yearRelativeTick(level.getGameTime(), seasonalOffset);
        double targetRate = SeasonalDayLength.targetRate(dayTimeMod, yearTick);

        // targetRate is in (0, 1): accumulate the per-tick shortfall and repay it one tick at a time.
        double shortfall = shortfallAccumulator.merge(level, 1.0 - targetRate, Double::sum);
        if (shortfall >= 1.0) {
            WorldClockUtil.setOverworldTime(level, level.getOverworldClockTime() - 1);
            shortfallAccumulator.put(level, shortfall - 1.0);
        }
    }
}
