package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: a discrete gain to health, hunger and/or energy just happened, so the client
 * pulses the matching HUD bars gold (purely visual; see {@code StatFlash}).
 *
 * @param mask bitmask of {@code StatFlash.HEALTH | HUNGER | ENERGY}
 */
public record StatFlashPacket(int mask) implements CustomPacketPayload {

    public static final Type<StatFlashPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "stat_flash"));

    public static final StreamCodec<FriendlyByteBuf, StatFlashPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeByte(packet.mask),
                    buf -> new StatFlashPacket(buf.readByte())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StatFlashPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.terrainlab.client.ClientStatFlash.trigger(packet.mask()));
    }
}
