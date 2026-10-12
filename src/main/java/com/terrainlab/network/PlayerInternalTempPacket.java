package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: the local player's internal temperature for the HUD. Accumulated server
 * state, so unlike heat sources it cannot be derived client-side.
 */
public record PlayerInternalTempPacket(float internalTemp) implements CustomPacketPayload {

    public static final Type<PlayerInternalTempPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "player_internal_temp"));

    public static final StreamCodec<FriendlyByteBuf, PlayerInternalTempPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeFloat(packet.internalTemp),
                    buf -> new PlayerInternalTempPacket(buf.readFloat())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerInternalTempPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.terrainlab.client.ClientInternalTempState.setInternalTemp(packet.internalTemp())
        );
    }
}
