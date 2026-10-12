package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Banked village XP: tier-up, biome-milestone and nightly rewards accumulate here and are
 * released as orbs at the bell at sunset. Tier-up reputation is applied immediately to players in range.
 */
public final class VillageBellRewardService {

    private VillageBellRewardService() {
    }

    public static void addPendingXp(VillageRecord record, int amount) {
        record.setPendingBellXp(record.getPendingBellXp() + amount);
    }

    /**
     * Handles a tier change. Downgrades cost nothing; only a tier's first-ever upgrade pays out,
     * tracked persistently in {@link VillageRecord#getXpGrantedTiers()}.
     */
    public static void onTierTransition(ServerLevel level, VillageId id, VillageRecord record,
                                         BlockPos bellPos, VillageTier previousTier, VillageTier newTier) {
        if (newTier.level() <= previousTier.level()) {
            return;
        }
        if (record.getXpGrantedTiers().contains(newTier)) {
            return; // already paid out, e.g. re-reached after a downgrade
        }

        int xp = tierUpXp(newTier);
        if (xp > 0) {
            addPendingXp(record, xp);
        }
        record.getXpGrantedTiers().add(newTier);
        VillageReputationService.applyToPlayersInRadius(level, bellPos, tierUpReputation(newTier));

        if (newTier == VillageTier.TIER_3_VILLAGE) {
            checkBiomeMilestone(level, bellPos, record);
        }
    }

    private static int tierUpReputation(VillageTier newTier) {
        return switch (newTier) {
            case TIER_2_HAMLET -> VillageConstants.TIER_UP_REPUTATION_LEVEL2;
            case TIER_3_VILLAGE -> VillageConstants.TIER_UP_REPUTATION_LEVEL3;
            case TIER_4_TOWN -> VillageConstants.TIER_UP_REPUTATION_LEVEL4;
            case TIER_1_OUTPOST -> 0;
        };
    }

    private static int tierUpXp(VillageTier newTier) {
        return switch (newTier) {
            case TIER_2_HAMLET -> VillageConstants.TIER_UP_XP_LEVEL2;
            case TIER_3_VILLAGE -> VillageConstants.TIER_UP_XP_LEVEL3;
            case TIER_4_TOWN -> VillageConstants.TIER_UP_XP_LEVEL4;
            case TIER_1_OUTPOST -> 0;
        };
    }

    /**
     * Awards the world-wide one-time bonus for the first tier-3 village of each villager biome
     * type, classified from the bell's biome via {@link VillagerType#byBiome}.
     */
    private static void checkBiomeMilestone(ServerLevel level, BlockPos bellPos, VillageRecord record) {
        Holder<Biome> biome = level.getBiome(bellPos);
        ResourceKey<VillagerType> type = VillagerType.byBiome(biome);
        VillageBiomeMilestoneData milestones = VillageBiomeMilestoneData.get(level.getServer().overworld());
        if (milestones.tryClaim(type)) {
            addPendingXp(record, VillageConstants.BIOME_MILESTONE_XP);
        }
    }

    /** Nightly bonus, banked at each sunset for the two highest tiers. */
    public static void addNightlyBonus(VillageRecord record) {
        int xp = switch (record.getTier()) {
            case TIER_4_TOWN -> VillageConstants.NIGHT_LEVEL3_XP;
            case TIER_3_VILLAGE -> VillageConstants.NIGHT_LEVEL2_XP;
            default -> 0;
        };
        if (xp > 0) {
            addPendingXp(record, xp);
        }
    }

    /** Spawns the banked XP as orbs at the bell. Called at solar sunset. */
    public static void releasePendingXp(ServerLevel level, BlockPos bellPos, VillageRecord record) {
        int amount = record.getPendingBellXp();
        if (amount <= 0) {
            return;
        }
        ExperienceOrb.award(level, Vec3.atCenterOf(bellPos), amount);
        record.setPendingBellXp(0);
    }
}
