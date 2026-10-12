package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Finds village anchors by locating Bell POIs ({@code PoiTypes.MEETING}). PoiManager has no
 * level-wide query, so the search is a union of fixed-radius scans around each online player;
 * villages with no player nearby are not discovered.
 */
public final class VillageDiscoveryService {

    private static final double SEARCH_RADIUS = 128.0; // blocks

    private VillageDiscoveryService() {
    }

    public static java.util.List<BlockPos> discoverBellPositions(ServerLevel level) {
        Set<BlockPos> found = new LinkedHashSet<>();
        PoiManager poiManager = level.getPoiManager();

        for (ServerPlayer player : level.players()) {
            BlockPos center = player.blockPosition();
            poiManager.findAll(
                    holder -> holder.is(PoiTypes.MEETING),
                    pos -> true,
                    center,
                    (int) SEARCH_RADIUS,
                    PoiManager.Occupancy.ANY
            ).forEach(found::add);
        }

        return java.util.List.copyOf(found);
    }
}
