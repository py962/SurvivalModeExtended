package com.terrainlab.village;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

/**
 * Computes a village's infrastructure score. Only the bell contributes for now, so every
 * village scores {@link VillageConstants#INFRASTRUCTURE_BELL_POINTS}.
 */
public final class VillageInfrastructureService {

    private VillageInfrastructureService() {
    }

    public static int computeScore(ServerLevel level, BlockPos bellPos) {
        int score = VillageConstants.INFRASTRUCTURE_BELL_POINTS;

        // TODO: add hearth and fountain points once those blocks exist.

        return score;
    }
}
