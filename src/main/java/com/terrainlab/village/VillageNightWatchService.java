package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Night-watch bonus: players within the bell's effective radius at dusk are snapshotted, and if
 * any of them is still in range at the next solar-day rollover (dawn), the village banks its
 * tier's night XP. Tracking is in-memory only; a restart forfeits that night's bonus.
 */
public final class VillageNightWatchService {

    private VillageNightWatchService() {
    }

    private record Tracking(boolean snapshotTakenThisDuskWindow, long lastKnownDay, Set<UUID> snapshot) {
    }

    private static final Map<VillageId, Tracking> TRACKING = new HashMap<>();

    /** Called on every economy poll with whether it is currently solar dusk. */
    public static void onPoll(ServerLevel level, VillageId id, BlockPos bellPos, VillageRecord record, boolean isDusk) {
        long currentDay = com.terrainlab.season.SolarTime.solarDayNumber(level);
        Tracking tracking = TRACKING.computeIfAbsent(id, k -> new Tracking(false, currentDay, Set.of()));

        if (currentDay != tracking.lastKnownDay()) {
            // Dawn: pay out if anyone snapshotted at dusk is still present.
            if (!tracking.snapshot().isEmpty()) {
                int tierXp = nightXpFor(record.getTier());
                if (tierXp > 0) {
                    Set<UUID> stillPresent = playersInRadius(level, bellPos);
                    boolean anyQualify = tracking.snapshot().stream().anyMatch(stillPresent::contains);
                    if (anyQualify) {
                        VillageBellRewardService.addPendingXp(record, tierXp);
                    }
                }
            }
            tracking = new Tracking(false, currentDay, Set.of());
        }

        if (isDusk && !tracking.snapshotTakenThisDuskWindow()) {
            tracking = new Tracking(true, tracking.lastKnownDay(), playersInRadius(level, bellPos));
        }

        TRACKING.put(id, tracking);
    }

    // Tier 4 uses the tier-3 rate.
    private static int nightXpFor(VillageTier tier) {
        return switch (tier) {
            case TIER_2_HAMLET -> VillageConstants.NIGHT_LEVEL2_XP;
            case TIER_3_VILLAGE, TIER_4_TOWN -> VillageConstants.NIGHT_LEVEL3_XP;
            case TIER_1_OUTPOST -> 0;
        };
    }

    private static Set<UUID> playersInRadius(ServerLevel level, BlockPos bellPos) {
        double radius = VillageMembershipService.effectiveRadius(level, bellPos);
        double radiusSq = radius * radius;
        Set<UUID> result = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(bellPos) <= radiusSq) {
                result.add(player.getUUID());
            }
        }
        return result;
    }
}
