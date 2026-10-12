package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.List;
import java.util.UUID;

/**
 * Applies village reputation changes as vanilla gossip on each member villager. The gossip
 * type and value are chosen so the weighted reputation change matches the requested amount as
 * closely as vanilla's per-type caps allow; vanilla decay still applies.
 */
public final class VillageReputationService {

    private VillageReputationService() {
    }

    /** Applies {@code amount} reputation to every player within the bell's effective radius, on every member villager. */
    public static void applyToPlayersInRadius(ServerLevel level, BlockPos bellPos, int amount) {
        if (amount == 0) {
            return;
        }
        double radius = VillageMembershipService.effectiveRadius(level, bellPos);
        double radiusSq = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().distSqr(bellPos) <= radiusSq) {
                applyToPlayer(level, bellPos, player.getUUID(), amount);
            }
        }
    }

    /** Applies {@code amount} reputation to one player on every villager of the village anchored at {@code bellPos}. */
    public static void applyToPlayer(ServerLevel level, BlockPos bellPos, UUID playerId, int amount) {
        if (amount == 0) {
            return;
        }
        GossipType type = gossipTypeFor(amount);
        // Reputation = value x type weight, so divide by the weight (MAJOR_POSITIVE weighs 5).
        int value = Math.max(1, Math.abs(amount) / Math.abs(type.weight));

        List<Villager> members = VillageMembershipService
                .assignVillagersToNearestVillage(level, List.of(bellPos))
                .getOrDefault(bellPos, List.of());
        for (Villager villager : members) {
            villager.getGossips().add(playerId, type, value);
        }
    }

    /**
     * Negative amounts use MINOR_NEGATIVE (weight -1, cap 200), which can express any amount
     * the economy uses. Positive amounts of 25 or more use MAJOR_POSITIVE, which never decays.
     */
    private static GossipType gossipTypeFor(int amount) {
        if (amount > 0) {
            return amount >= GossipType.MINOR_POSITIVE.max ? GossipType.MAJOR_POSITIVE : GossipType.MINOR_POSITIVE;
        }
        return GossipType.MINOR_NEGATIVE;
    }
}
