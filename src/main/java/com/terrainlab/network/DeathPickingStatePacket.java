package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: Death amulet location-picking state. {@code active} toggles the client
 * confirm prompt; {@code ticksRemaining} drives its countdown.
 */
public record DeathPickingStatePacket(boolean active, int ticksRemaining) implements CustomPacketPayload {

    public static final Type<DeathPickingStatePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "death_picking_state"));

    public static final StreamCodec<FriendlyByteBuf, DeathPickingStatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBoolean(packet.active);
                        buf.writeVarInt(packet.ticksRemaining);
                    },
                    buf -> new DeathPickingStatePacket(buf.readBoolean(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeathPickingStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.terrainlab.client.ClientDeathPickingState.set(packet.active(), packet.ticksRemaining()));
    }
}
