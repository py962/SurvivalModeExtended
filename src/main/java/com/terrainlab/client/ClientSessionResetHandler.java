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
        ClientDeathOptionsState.reset();
        ClientStatFlash.reset();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientDeathOptionsState.reset();
    }

    /** Fires when the local player is recreated (respawn or dimension change): the death is over. */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        ClientDeathOptionsState.reset();
    }
}
