package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assigns villagers to the nearest discovered village bell within that bell's
 * {@link #effectiveRadius(ServerLevel, BlockPos)}. All membership checks share that radius.
 */
public final class VillageMembershipService {

    private VillageMembershipService() {
    }

    public static Map<BlockPos, List<Villager>> assignVillagersToNearestVillage(
            ServerLevel level, List<BlockPos> bellPositions) {

        Map<BlockPos, List<Villager>> membership = new HashMap<>();
        if (bellPositions.isEmpty()) {
            return membership;
        }
        for (BlockPos bell : bellPositions) {
            membership.put(bell, new ArrayList<>());
        }

        Map<BlockPos, Double> radiusByBell = new HashMap<>();
        for (BlockPos bell : bellPositions) {
            radiusByBell.put(bell, effectiveRadius(level, bell));
        }

        for (BlockPos bell : bellPositions) {
            double radius = radiusByBell.get(bell);
            AABB searchBox = new AABB(bell).inflate(radius);
            List<Villager> nearby = level.getEntitiesOfClass(Villager.class, searchBox, v -> true);

            for (Villager villager : nearby) {
                BlockPos nearestBell = nearestBellWithinRadius(villager.blockPosition(), bellPositions, radiusByBell);
                if (nearestBell != null) {
                    List<Villager> members = membership.get(nearestBell);
                    if (!members.contains(villager)) {
                        members.add(villager);
                    }
                }
            }
        }

        return membership;
    }

    private static BlockPos nearestBellWithinRadius(
            BlockPos villagerPos, List<BlockPos> bellPositions, Map<BlockPos, Double> radiusByBell) {
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (BlockPos bell : bellPositions) {
            double radius = radiusByBell.get(bell);
            double distSq = bell.distSqr(villagerPos);
            if (distSq <= radius * radius && distSq < nearestDistSq) {
                nearest = bell;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    /**
     * Membership radius for a bell (blocks): half the distance to the nearest other known bell in
     * this level, clamped between {@link VillageConstants#MIN_VILLAGE_MEMBERSHIP_RADIUS} and
     * {@link VillageConstants#MAX_VILLAGE_MEMBERSHIP_RADIUS}. Uses every known bell, not just the caller's list, so all
     * call sites agree.
     */
    public static double effectiveRadius(ServerLevel level, BlockPos bell) {
        double nearestOtherBellDist = Double.MAX_VALUE;
        for (VillageId id : VillageSavedData.get(level).knownVillageIds()) {
            if (id.bellPos().equals(bell) || !id.dimension().equals(level.dimension())) {
                continue;
            }
            double dist = Math.sqrt(id.bellPos().distSqr(bell));
            if (dist < nearestOtherBellDist) {
                nearestOtherBellDist = dist;
            }
        }

        if (nearestOtherBellDist == Double.MAX_VALUE) {
            return VillageConstants.MIN_VILLAGE_MEMBERSHIP_RADIUS;
        }
        return Math.clamp(nearestOtherBellDist / 2.0,
                VillageConstants.MIN_VILLAGE_MEMBERSHIP_RADIUS, VillageConstants.MAX_VILLAGE_MEMBERSHIP_RADIUS);
    }

    /**
     * Returns the bell whose effective radius contains {@code pos}, preferring the nearest one.
     */
    public static Optional<BlockPos> findMemberBell(ServerLevel level, BlockPos pos) {
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        for (VillageId id : VillageSavedData.get(level).knownVillageIds()) {
            if (!id.dimension().equals(level.dimension())) {
                continue;
            }
            BlockPos bell = id.bellPos();
            double radius = effectiveRadius(level, bell);
            double distSq = bell.distSqr(pos);
            if (distSq <= radius * radius && distSq < nearestDistSq) {
                nearest = bell;
                nearestDistSq = distSq;
            }
        }
        return Optional.ofNullable(nearest);
    }
}
