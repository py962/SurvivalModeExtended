package com.terrainlab.celestial;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;

/**
 * Shield conduits put fire out promptly: fire (and soul fire) placed or spreading inside an active
 * Shield conduit's range is removed the moment it appears, and existing fire is removed on its next
 * fire tick (BaseFireBlockShieldMixin, FireBlockShieldMixin). The conduit's slower climate sweep in
 * CelestialEffects still clears fire that never ticks, such as soul fire lit before the conduit
 * was active. A nether portal lit inside the range still forms, since the portal replaces the fire
 * before this check runs.
 */
public final class ShieldFireSuppression {

    private ShieldFireSuppression() {
    }

    /** Removes the fire at pos if it is inside an active Shield conduit's range. True if removed. */
    public static boolean extinguishIfShielded(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof BaseFireBlock)) {
            return false;
        }
        if (!CelestialConduitRegistry.get(level).hasActiveNearby(CelestialVariant.SHIELD, level.dimension(), pos)) {
            return false;
        }
        level.removeBlock(pos, false);
        return true;
    }
}
