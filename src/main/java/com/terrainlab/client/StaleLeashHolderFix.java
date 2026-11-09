package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-only. Vanilla's client resolves a leash holder from its entity id once and then keeps that
 * entity object. If the holder is later removed from the client and re-added as a new object with
 * the same id (it left this client's tracking range, which for trail guides is half a donkey's,
 * then came back, for example by teleporting to the player), the lead keeps pointing at the
 * removed object, frozen where it was last seen. The rope then runs off from the donkey to the
 * guide's old position and never recovers.
 * <p>
 * Twice a second this finds leashed entities whose holder object has been removed and points them
 * back at the holder's id, so the lead re-attaches to the live entity (or draws no rope until it
 * is loaded again).
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class StaleLeashHolderFix {

    private static final boolean DEBUG_LOGGING = false;
    private static final int CHECK_INTERVAL_TICKS = 10;

    private static int tickCounter;

    private StaleLeashHolderFix() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Leashable leashable)) continue;
            Leashable.LeashData data = leashable.getLeashData();
            if (data == null || data.leashHolder == null || !data.leashHolder.isRemoved()) continue;

            Entity stale = data.leashHolder;
            leashable.setDelayedLeashHolderId(stale.getId());
            if (DEBUG_LOGGING) {
                Entity live = level.getEntity(stale.getId());
                TerrainLab.LOGGER.info(
                        "[StaleLeash] {} id={} had removed holder {} id={} at ({}, {}, {}); live holder {}",
                        entity.getType().toShortString(), entity.getId(),
                        stale.getType().toShortString(), stale.getId(),
                        String.format("%.1f", stale.getX()), String.format("%.1f", stale.getY()),
                        String.format("%.1f", stale.getZ()),
                        live == null ? "not loaded" : String.format("at (%.1f, %.1f, %.1f)",
                                live.getX(), live.getY(), live.getZ()));
            }
        }
    }
}
