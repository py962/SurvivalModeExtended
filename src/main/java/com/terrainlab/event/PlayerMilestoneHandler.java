package com.terrainlab.event;

import com.terrainlab.player.PlayerMilestoneData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Dimension XP milestones: a bonus on each player's first arrival in a dimension (once per
 * dimension) and a larger one-time bonus for the first return from the End to the Overworld.
 * Only actual dimension changes count, not the initial spawn.
 */
public final class PlayerMilestoneHandler {

    private PlayerMilestoneHandler() {
    }

    private static final int DIMENSION_FIRST_VISIT_XP = 500; // XP points
    private static final int END_RETURN_XP = 10000; // XP points

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        ResourceKey<Level> from = event.getFrom();
        ResourceKey<Level> to = event.getTo();

        // ServerPlayer has no getServer() in this version; go through its ServerLevel.
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        PlayerMilestoneData data = PlayerMilestoneData.get(server.overworld());

        if (data.tryClaimDimensionVisit(player.getUUID(), to)) {
            player.giveExperiencePoints(DIMENSION_FIRST_VISIT_XP);
        }

        if (from.equals(Level.END) && to.equals(Level.OVERWORLD) && data.tryClaimEndReturn(player.getUUID())) {
            player.giveExperiencePoints(END_RETURN_XP);
        }
    }
}
