package com.terrainlab.event;

import com.terrainlab.village.VillageBellRewardService;
import com.terrainlab.village.VillageConstants;
import com.terrainlab.village.VillageRecord;
import com.terrainlab.village.VillageReputationService;
import com.terrainlab.village.VillageSavedData;
import com.terrainlab.village.VillageTierTransitionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Village economy events: tier-up rewards and the bell-destruction
 * reputation penalty. Banked bell XP is released at solar sunset by
 * VillageDailyTickHandler.
 */
public final class VillageEconomyHandler {

    private VillageEconomyHandler() {
    }

    @SubscribeEvent
    public static void onVillageTierTransition(VillageTierTransitionEvent event) {
        VillageRecord record = VillageSavedData.get(event.getLevel()).getOrCreate(event.getVillageId());
        VillageBellRewardService.onTierTransition(
                event.getLevel(), event.getVillageId(), record, event.getVillageId().bellPos(),
                event.getPreviousTier(), event.getNewTier());
    }

    /** Breaking a bell costs the player reputation with that village. */
    @SubscribeEvent
    public static void onBellBroken(BreakBlockEvent event) {
        if (!event.getState().is(Blocks.BELL)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos bellPos = event.getPos();
        VillageReputationService.applyToPlayer(level, bellPos, event.getPlayer().getUUID(),
                VillageConstants.DESTROY_BELL_REPUTATION);
        VillageSavedData.get(level).remove(new com.terrainlab.village.VillageId(level.dimension(), bellPos));
    }
}
