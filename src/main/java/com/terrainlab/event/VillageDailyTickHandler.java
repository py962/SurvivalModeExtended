package com.terrainlab.event;

import com.terrainlab.season.SolarTime;
import com.terrainlab.village.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily village cycle.
 * <ul>
 *   <li>Hourly while the sun is up: a happiness sample per village, and the village food chest
 *       is ensured next to each bell (also at sunset; see VillageChestService).</li>
 *   <li>At solar sunset: the hunger and tier checks (using the day's
 *       average happiness), then the nightly bonus for the top two tiers
 *       is banked and all banked bell XP is released at the bell.</li>
 * </ul>
 * If the server starts or a village is first seen after sunset, the
 * sunset step runs on the first tick of that night instead.
 */
public final class VillageDailyTickHandler {

    /** Solar day whose sunset has already been handled, per dimension. */
    private static final Map<ResourceKey<Level>, Long> LAST_SUNSET = new HashMap<>();

    private VillageDailyTickHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        boolean sampleTick = tick % VillageConstants.HAPPINESS_SAMPLE_INTERVAL_TICKS == 0;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            boolean day = SolarTime.isSolarDay(level);

            if (!day) {
                long solarDay = SolarTime.solarDayNumber(level);
                Long done = LAST_SUNSET.get(level.dimension());
                if (done == null || done != solarDay) {
                    LAST_SUNSET.put(level.dimension(), solarDay);
                    runSunset(level, solarDay);
                }
            } else if (sampleTick) {
                sampleHappiness(level);
            }
        }
    }

    /** Forgets villages whose bell has been removed (only checked where the chunk is loaded). */
    private static void pruneMissingBells(ServerLevel level) {
        VillageSavedData saved = VillageSavedData.get(level);
        for (VillageId id : List.copyOf(saved.knownVillageIds())) {
            BlockPos bell = id.bellPos();
            if (level.isLoaded(bell) && !level.getBlockState(bell).is(net.minecraft.world.level.block.Blocks.BELL)) {
                saved.remove(id);
            }
        }
    }

    private static void sampleHappiness(ServerLevel level) {
        List<BlockPos> bells = VillageDiscoveryService.discoverBellPositions(level);
        if (bells.isEmpty()) {
            return;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        Map<BlockPos, List<Villager>> membership =
                VillageMembershipService.assignVillagersToNearestVillage(level, bells);
        long now = level.getGameTime();
        for (BlockPos bell : bells) {
            // Every discovered village gets its food chest (placed once; re-placed if broken).
            VillageChestService.ensureChest(level, bell);
            List<Villager> members = membership.getOrDefault(bell, List.of());
            if (!members.isEmpty()) {
                saved.getOrCreate(new VillageId(level.dimension(), bell))
                        .addHappinessSample(VillageHappinessService.computeHappiness(members, now));
            }
        }
        saved.setDirty();
    }

    private static void runSunset(ServerLevel level, long solarDay) {
        pruneMissingBells(level);
        List<BlockPos> bells = VillageDiscoveryService.discoverBellPositions(level);
        if (bells.isEmpty()) {
            return;
        }
        VillageSavedData saved = VillageSavedData.get(level);
        Map<BlockPos, List<Villager>> membership =
                VillageMembershipService.assignVillagersToNearestVillage(level, bells);

        for (BlockPos bell : bells) {
            VillageId id = new VillageId(level.dimension(), bell);
            VillageRecord record = saved.getOrCreate(id);
            VillageChestService.ensureChest(level, bell);
            if (record.getLastCheckedDay() != solarDay) {
                List<Villager> members = membership.getOrDefault(bell, List.of());
                VillageHungerService.runDailyCheck(level, id, members);
                VillageTierService.runDailyCheck(level, id, members);
                record.setLastCheckedDay(solarDay);
            }
            VillageBellRewardService.addNightlyBonus(record);
            VillageBellRewardService.releasePendingXp(level, bell, record);
        }
        saved.setDirty();
    }
}
