package com.terrainlab.item;

import com.terrainlab.TerrainLab;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Static world-overview map showing land, sea and broad biome colours, generated once and never updated.
 * Extends {@link MapItem} to reuse vanilla map rendering.
 * <p>
 * Generation is started by {@link com.terrainlab.event.AtlasAcquisitionHandler} when the stack enters an
 * inventory and runs on a background thread via {@link AtlasGenerationQueue}; {@link #use} only reports
 * progress or queues a stack the scan has not reached yet, and never generates on the server thread.
 */
public class AtlasItem extends MapItem {

    public AtlasItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()
                || !(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = player.getItemInHand(hand);

        if (stack.get(DataComponents.MAP_ID) != null) {
            return InteractionResult.PASS;
        }

        if (AtlasGenerationQueue.isPending(stack)) {
            serverPlayer.sendOverlayMessage(Component.literal("This Atlas is still being surveyed..."));
            return InteractionResult.PASS;
        }

        // Safety net for a stack the once-a-second acquisition scan has not reached yet.
        // TerrainLab.LOGGER.info(
        //         "[Atlas] use() reached an untouched stack before the acquisition scan"
        //                 + " caught it - queuing now. player="
        //                 + serverPlayer.getScoreboardName());
        AtlasGenerationQueue.ensureQueued(serverLevel, serverPlayer, stack);
        serverPlayer.sendOverlayMessage(Component.literal("Surveying the region for your Atlas..."));
        return InteractionResult.PASS;
    }

    @Override
    public void update(Level level, Entity entity, MapItemSavedData data) {
        // No-op: the Atlas is a fixed snapshot, so exploration updates (and MapItemMixin's
        // spyglass fill at the TAIL of super.update) must not run.

    }
}
