package com.terrainlab.village;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Per-villager "trapped" state. Tracks the maximum horizontal (X/Z) distance a villager wanders
 * from its position at the start of each day; a day counts as trapped if that never reaches
 * {@link VillageConstants#TRAPPED_WANDER_THRESHOLD_BLOCKS}, and the villager is flagged only after
 * {@link VillageConstants#TRAPPED_CONFIRM_STREAK_DAYS} consecutive trapped days.
 */
public final class VillagerTrappedService {

    private VillagerTrappedService() {
    }

    public static void update(Villager villager, ServerLevel level) {
        long currentDay = level.getDayTime() / VillageConstants.DAY_LENGTH_TICKS;
        long lastDay = villager.getData(ModAttachments.VILLAGER_WANDER_LAST_DAY.get());
        BlockPos pos = villager.blockPosition();

        if (lastDay < 0) {
            // First read: start tracking without judging a partial day.
            resetTracking(villager, pos, currentDay);
            return;
        }

        if (currentDay != lastDay) {
            // Day rolled over: evaluate the completed day, then reset.
            float maxDist = villager.getData(ModAttachments.VILLAGER_WANDER_MAX_DIST.get());
            boolean trappedToday = maxDist < VillageConstants.TRAPPED_WANDER_THRESHOLD_BLOCKS;

            int streak = villager.getData(ModAttachments.VILLAGER_TRAPPED_STREAK_DAYS.get());
            streak = trappedToday ? streak + 1 : 0;
            villager.setData(ModAttachments.VILLAGER_TRAPPED_STREAK_DAYS.get(), streak);

            boolean reported = streak >= VillageConstants.TRAPPED_CONFIRM_STREAK_DAYS;
            villager.setData(ModAttachments.VILLAGER_IS_TRAPPED.get(), reported);

            // TerrainLab.LOGGER.info(
            //         "[TRAPPED_DEBUG] villager={} day={} maxDistFromDayStart={} threshold={} trappedToday={} streak={} reported={}",
            //         villager.getUUID(), lastDay, maxDist, VillageConstants.TRAPPED_WANDER_THRESHOLD_BLOCKS,
            //         trappedToday, streak, reported);

            resetTracking(villager, pos, currentDay);
            return;
        }

        // Same day: update the running maximum distance from the day's start.
        BlockPos startPos = villager.getData(ModAttachments.VILLAGER_WANDER_DAY_START_POS.get());
        double dx = pos.getX() - startPos.getX();
        double dz = pos.getZ() - startPos.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float maxDist = villager.getData(ModAttachments.VILLAGER_WANDER_MAX_DIST.get());
        if (dist > maxDist) {
            villager.setData(ModAttachments.VILLAGER_WANDER_MAX_DIST.get(), (float) dist);
        }
    }

    private static void resetTracking(Villager villager, BlockPos pos, long currentDay) {
        villager.setData(ModAttachments.VILLAGER_WANDER_DAY_START_POS.get(), pos);
        villager.setData(ModAttachments.VILLAGER_WANDER_MAX_DIST.get(), 0.0F);
        villager.setData(ModAttachments.VILLAGER_WANDER_LAST_DAY.get(), currentDay);
    }

    /** Whether the villager has been trapped for the required number of consecutive days. */
    public static boolean isTrapped(Villager villager) {
        return villager.getData(ModAttachments.VILLAGER_IS_TRAPPED.get());
    }
}
