package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/** Holds back village breeding while too many villagers have no bed. */
public final class VillageBeds {

    // Bedless villagers a village tolerates before breeding stops.
    public static final int BEDLESS_MARGIN = 2;
    // Same search range vanilla uses when taking a bed for a newborn.
    public static final int BED_SEARCH_RANGE = 48;

    private VillageBeds() {
    }

    /**
     * Villagers without a bed in the same village as {@code villager}, or within the bed search
     * range when it belongs to no known village.
     */
    public static int bedlessNeighbours(ServerLevel level, Villager villager) {
        Optional<BlockPos> bell = VillageMembershipService.findMemberBell(level, villager.blockPosition());
        AABB area = bell
                .map(b -> new AABB(b).inflate(VillageMembershipService.effectiveRadius(level, b)))
                .orElseGet(() -> villager.getBoundingBox().inflate(BED_SEARCH_RANGE));

        int bedless = 0;
        for (Villager other : level.getEntitiesOfClass(Villager.class, area, Villager::isAlive)) {
            if (other.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) {
                continue;
            }
            if (bell.isPresent()
                    && !bell.equals(VillageMembershipService.findMemberBell(level, other.blockPosition()))) {
                continue;
            }
            bedless++;
        }
        return bedless;
    }
}
