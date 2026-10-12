package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: the local player's energy value for the energy HUD bar. PLAYER_ENERGY
 * is not client-synced, so this is its only path to the client.
 */
public record PlayerEnergyPacket(float energy) implements CustomPacketPayload {

    public static final Type<PlayerEnergyPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "player_energy"));

    public static final StreamCodec<FriendlyByteBuf, PlayerEnergyPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeFloat(packet.energy),
                    buf -> new PlayerEnergyPacket(buf.readFloat())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlayerEnergyPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.terrainlab.client.ClientEnergyState.setEnergy(packet.energy())
        );
    }
}
