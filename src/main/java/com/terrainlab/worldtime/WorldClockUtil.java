package com.terrainlab.worldtime;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.neoforged.neoforge.common.util.ClockAdjustment;

/**
 * Helpers for the 26.x world clock system, which replaced Level#setDayTime.
 */
public final class WorldClockUtil {

    private WorldClockUtil() {
    }

    public static Holder<WorldClock> overworldClock(ServerLevel level) {
        return level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
    }

    /** Replacement for the old {@code ServerLevel#setDayTime(long)} (overworld clock). */
    public static void setOverworldTime(ServerLevel level, long ticks) {
        level.clockManager().setTotalTicks(overworldClock(level), ticks);
    }

    /**
     * Computes the overworld clock time that {@code adjustment} would produce from {@code currentTicks},
     * without applying it. Mirrors {@link ClockAdjustment#apply} / ServerClockManager#moveToTimeMarker.
     */
    public static long resolveOverworldAdjustment(ServerLevel level, ClockAdjustment adjustment, long currentTicks) {
        if (adjustment instanceof ClockAdjustment.Absolute(long ticks)) {
            return ticks;
        }
        if (adjustment instanceof ClockAdjustment.Relative(long ticks)) {
            return currentTicks + ticks;
        }
        if (adjustment instanceof ClockAdjustment.Marker(ResourceKey<ClockTimeMarker> markerKey)) {
            Holder<WorldClock> clock = overworldClock(level);
            ClockTimeMarker[] found = new ClockTimeMarker[1];
            level.registryAccess().lookupOrThrow(Registries.TIMELINE).listElements().forEach(timeline ->
                    timeline.value().registerTimeMarkers((key, marker) -> {
                        if (key.equals(markerKey) && marker.clock().equals(clock)) {
                            found[0] = marker;
                        }
                    }));
            ClockTimeMarker marker = found[0];
            if (marker == null || marker.occursAt(currentTicks)) {
                return currentTicks;
            }
            return marker.resolveTimeToMoveTo(currentTicks);
        }
        return currentTicks;
    }
}
