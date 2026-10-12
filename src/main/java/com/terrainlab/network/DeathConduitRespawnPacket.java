package com.terrainlab.network;

import com.terrainlab.celestial.DeathRespawnManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S marker: the death screen's "Respawn at Death Conduit" button was pressed.
 * Sent before the vanilla respawn packet on the same connection, so
 * DeathRespawnManager records the choice before PlayerEvent.Clone runs.
 */
public record DeathConduitRespawnPacket() implements CustomPacketPayload {

    public static final Type<DeathConduitRespawnPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "death_conduit_respawn"));

    public static final StreamCodec<FriendlyByteBuf, DeathConduitRespawnPacket> STREAM_CODEC =
            StreamCodec.unit(new DeathConduitRespawnPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeathConduitRespawnPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DeathRespawnManager.chooseConduitRespawn(player.getUUID());
            }
        });
    }
}
