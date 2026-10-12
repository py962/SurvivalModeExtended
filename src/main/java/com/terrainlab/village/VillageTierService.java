package com.terrainlab.village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * Daily tier evaluation. Computes population, day-averaged happiness, infrastructure and trade
 * quality, moves the village at most one tier per day, and posts a
 * {@link VillageTierTransitionEvent} on any change.
 */
public final class VillageTierService {

    private VillageTierService() {
    }

    public static void runDailyCheck(ServerLevel level, VillageId id, List<Villager> members) {
        VillageRecord record = VillageSavedData.get(level).getOrCreate(id);

        int size = members.size();
        // Day's average of daytime samples; falls back to a live reading if none were taken.
        double happiness = record.takeDayAverageHappiness(
                VillageHappinessService.computeHappiness(members, level.getGameTime()));
        int infrastructure = VillageInfrastructureService.computeScore(level, id.bellPos());
        int tradeQuality = VillageTradeQualityService.computeScore(members);

        VillageTierRequirements.Metrics metrics =
                new VillageTierRequirements.Metrics(size, happiness, infrastructure, tradeQuality);

        VillageTier currentTier = record.getTier();
        VillageTier newTier = applyHysteresis(currentTier, metrics);

        if (newTier != currentTier) {
            record.setTier(newTier);
            NeoForge.EVENT_BUS.post(new VillageTierTransitionEvent(level, id, currentTier, newTier, members));
        }

        // No composite score exists under the AND-gated design; the persisted field is kept at 0.
        record.setLastCompositeScore(0.0);
    }

    /**
     * Highest tier whose requirements are all met, checked top-down. Assumes each tier's
     * minimums are at least as strict as the tier below.
     */
    private static VillageTier highestTierMetBy(VillageTierRequirements.Metrics metrics) {
        if (VillageTierRequirements.TIER_4_TOWN.isMetBy(metrics)) return VillageTier.TIER_4_TOWN;
        if (VillageTierRequirements.TIER_3_VILLAGE.isMetBy(metrics)) return VillageTier.TIER_3_VILLAGE;
        if (VillageTierRequirements.TIER_2_HAMLET.isMetBy(metrics)) return VillageTier.TIER_2_HAMLET;
        return VillageTier.TIER_1_OUTPOST;
    }

    /**
     * Moves at most one tier per check: up when the next tier's requirements are met, down when
     * the current tier's requirements are missed by more than their margins.
     */
    private static VillageTier applyHysteresis(VillageTier current, VillageTierRequirements.Metrics metrics) {
        VillageTier candidate = highestTierMetBy(metrics);

        if (candidate.level() > current.level()) {
            VillageTier next = current.next();
            VillageTierRequirements nextReq = VillageTierRequirements.forTier(next);
            return nextReq.isClearedWithMarginBy(metrics) ? next : current;
        }
        if (candidate.level() < current.level()) {
            VillageTierRequirements currentReq = VillageTierRequirements.forTier(current);
            if (currentReq == null) {
                // Outpost cannot be demoted further.
                return current;
            }
            return currentReq.isFailedWithMarginBy(metrics) ? current.previous() : current;
        }
        return current;
    }
}
