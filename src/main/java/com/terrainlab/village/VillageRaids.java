package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Raids only happen at villages of tier 2 (Hamlet) or above. */
public final class VillageRaids {
    public static final int MIN_RAID_TIER = VillageTier.TIER_2_HAMLET.level();

    private VillageRaids() {}

    /** True if {@code pos} lies inside a known village whose tier is high enough to be raided. */
    public static boolean canBeRaided(ServerLevel level, BlockPos pos) {
        return VillageMembershipService.findMemberBell(level, pos)
                .flatMap(bell -> VillageSavedData.get(level).find(new VillageId(level.dimension(), bell)))
                .map(record -> record.getTier().level() >= MIN_RAID_TIER)
                .orElse(false);
    }
}
