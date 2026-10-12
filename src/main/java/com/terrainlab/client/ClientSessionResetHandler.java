package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Clears client-side mirrors of server state on disconnect, so nothing leaks into the next world. */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class ClientSessionResetHandler {

    private ClientSessionResetHandler() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDeathPickingState.reset();
        ClientAuroraState.reset();
    }
}
