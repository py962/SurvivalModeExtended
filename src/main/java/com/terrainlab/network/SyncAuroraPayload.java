package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: whether the aurora is currently active.
 */
public record SyncAuroraPayload(boolean active) implements CustomPacketPayload {

    public static final Type<SyncAuroraPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "sync_aurora"));

    public static final StreamCodec<FriendlyByteBuf, SyncAuroraPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBoolean(packet.active),
                    buf -> new SyncAuroraPayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncAuroraPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {

            com.terrainlab.client.ClientAuroraState.setActive(packet.active());
        });
    }
}
