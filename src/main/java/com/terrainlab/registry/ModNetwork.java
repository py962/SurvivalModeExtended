package com.terrainlab.registry;

import com.terrainlab.network.GlyphSelectionPacket;
import com.terrainlab.network.HorseSprintInputPacket;
import com.terrainlab.network.SeasonOffsetPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers all custom network payloads and their handlers (protocol version "1.0"). */
public class ModNetwork {

    public static void register(IEventBus eventBus) {
        eventBus.addListener(ModNetwork::registerPackets);
    }

    private static void registerPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1.0");
        registrar.playToServer(
                GlyphSelectionPacket.TYPE,
                GlyphSelectionPacket.STREAM_CODEC,
                GlyphSelectionPacket::handle
        );
        registrar.playToServer(
                HorseSprintInputPacket.TYPE,
                HorseSprintInputPacket.STREAM_CODEC,
                HorseSprintInputPacket::handle
        );
        registrar.playToClient(
                com.terrainlab.network.SyncAuroraPayload.TYPE,
                com.terrainlab.network.SyncAuroraPayload.STREAM_CODEC,
                com.terrainlab.network.SyncAuroraPayload::handle
        );
        registrar.playToServer(
                com.terrainlab.network.HowdahSleepRequestPacket.TYPE,
                com.terrainlab.network.HowdahSleepRequestPacket.STREAM_CODEC,
                com.terrainlab.network.HowdahSleepRequestPacket::handle
        );

        registrar.playToServer(
                com.terrainlab.network.EnergyInputPacket.TYPE,
                com.terrainlab.network.EnergyInputPacket.STREAM_CODEC,
                com.terrainlab.network.EnergyInputPacket::handle
        );
        registrar.playToClient(
                SeasonOffsetPacket.TYPE,
                SeasonOffsetPacket.STREAM_CODEC,
                SeasonOffsetPacket::handle
        );
        registrar.playToClient(
                com.terrainlab.network.PlayerInternalTempPacket.TYPE,
                com.terrainlab.network.PlayerInternalTempPacket.STREAM_CODEC,
                com.terrainlab.network.PlayerInternalTempPacket::handle
        );
        // Energy HUD sync; PLAYER_ENERGY is not client-synced, so this is its only path to the client.
        registrar.playToClient(
                com.terrainlab.network.PlayerEnergyPacket.TYPE,
                com.terrainlab.network.PlayerEnergyPacket.STREAM_CODEC,
                com.terrainlab.network.PlayerEnergyPacket::handle
        );
        registrar.playToServer(
                com.terrainlab.network.WaterDrinkInputPacket.TYPE,
                com.terrainlab.network.WaterDrinkInputPacket.STREAM_CODEC,
                com.terrainlab.network.WaterDrinkInputPacket::handle
        );

        // Celestial Death respawn choice flow (see DeathRespawnManager).
        registrar.playToServer(
                com.terrainlab.network.DeathConduitRespawnPacket.TYPE,
                com.terrainlab.network.DeathConduitRespawnPacket.STREAM_CODEC,
                com.terrainlab.network.DeathConduitRespawnPacket::handle
        );
        registrar.playToServer(
                com.terrainlab.network.DeathStartPickingPacket.TYPE,
                com.terrainlab.network.DeathStartPickingPacket.STREAM_CODEC,
                com.terrainlab.network.DeathStartPickingPacket::handle
        );
        registrar.playToServer(
                com.terrainlab.network.DeathConfirmPickingPacket.TYPE,
                com.terrainlab.network.DeathConfirmPickingPacket.STREAM_CODEC,
                com.terrainlab.network.DeathConfirmPickingPacket::handle
        );
        registrar.playToClient(
                com.terrainlab.network.DeathPickingStatePacket.TYPE,
                com.terrainlab.network.DeathPickingStatePacket.STREAM_CODEC,
                com.terrainlab.network.DeathPickingStatePacket::handle
        );
        registrar.playToClient(
                com.terrainlab.network.DeathOptionsPacket.TYPE,
                com.terrainlab.network.DeathOptionsPacket.STREAM_CODEC,
                com.terrainlab.network.DeathOptionsPacket::handle
        );

        // Client mirror of the heat source index (see HeatSourceSyncPacket).
        registrar.playToClient(
                com.terrainlab.network.HeatSourceSyncPacket.TYPE,
                com.terrainlab.network.HeatSourceSyncPacket.STREAM_CODEC,
                com.terrainlab.network.HeatSourceSyncPacket::handle
        );
        registrar.playToClient(
                com.terrainlab.network.HeatSourceRemovePacket.TYPE,
                com.terrainlab.network.HeatSourceRemovePacket.STREAM_CODEC,
                com.terrainlab.network.HeatSourceRemovePacket::handle
        );

        // Trail Trader guide dialogue.
        registrar.playToClient(
                com.terrainlab.network.TrailTraderDialoguePacket.TYPE,
                com.terrainlab.network.TrailTraderDialoguePacket.STREAM_CODEC,
                com.terrainlab.network.TrailTraderDialoguePacket::handle
        );

        // Wood shields: flash the raised pose on a passive auto-block.
        registrar.playToClient(
                com.terrainlab.network.ShieldAutoBlockPacket.TYPE,
                com.terrainlab.network.ShieldAutoBlockPacket.STREAM_CODEC,
                com.terrainlab.network.ShieldAutoBlockPacket::handle
        );

        // Ancient Codex: success/failure of a 3-glyph attempt, for the screen animation.
        registrar.playToClient(
                com.terrainlab.network.CodexResultPacket.TYPE,
                com.terrainlab.network.CodexResultPacket.STREAM_CODEC,
                com.terrainlab.network.CodexResultPacket::handle
        );

        // Replicator: the GUI button counts as one redstone pulse.
        registrar.playToServer(
                com.terrainlab.network.ReplicatorPulsePacket.TYPE,
                com.terrainlab.network.ReplicatorPulsePacket.STREAM_CODEC,
                com.terrainlab.network.ReplicatorPulsePacket::handle
        );
    }
}
