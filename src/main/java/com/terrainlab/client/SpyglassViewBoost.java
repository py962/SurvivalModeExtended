package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Raises the render distance to {@link #SCOPED_DISTANCE} chunks while the player looks through a
 * spyglass, without touching the saved setting. Read by OptionsSpyglassViewMixin and
 * IntegratedServerSpyglassViewMixin.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class SpyglassViewBoost {

    public static final int SCOPED_DISTANCE = 32;

    private static volatile boolean active;

    private SpyglassViewBoost() {
    }

    public static int apply(int renderDistance) {
        return active ? Math.max(renderDistance, SCOPED_DISTANCE) : renderDistance;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean scoping = minecraft.player != null && minecraft.player.isScoping();
        if (scoping != active) {
            active = scoping;
            // The server sizes the chunks it sends from the reported view distance.
            minecraft.options.broadcastOptions();
        }
    }
}
