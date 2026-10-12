package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S2C: the dialogue line a Trail Trader is showing to the trading player.
 * An empty translation key clears the dialogue when the trading session ends.
 */
public record TrailTraderDialoguePacket(
        UUID traderId,
        String translationKey
) implements CustomPacketPayload {

    public static final Type<TrailTraderDialoguePacket> TYPE =
            new Type<>(
                    Identifier.fromNamespaceAndPath(
                            "terrainlab",
                            "trail_trader_dialogue"
                    )
            );

    public static final StreamCodec<FriendlyByteBuf, TrailTraderDialoguePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeUUID(packet.traderId());
                        buf.writeUtf(packet.translationKey(), 256);
                    },
                    buf -> new TrailTraderDialoguePacket(
                            buf.readUUID(),
                            buf.readUtf(256)
                    )
            );

    /**
     * Creates a packet that clears the client's stored Trail Trader dialogue.
     */
    public static TrailTraderDialoguePacket clear(UUID traderId) {
        return new TrailTraderDialoguePacket(
                traderId,
                ""
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            TrailTraderDialoguePacket packet,
            IPayloadContext context) {

        context.enqueueWork(() -> {

            if (packet.translationKey().isEmpty()) {
                com.terrainlab.client.ClientTrailTraderDialogueState.clear();
                return;
            }

            com.terrainlab.client.ClientTrailTraderDialogueState.setCurrentLine(
                    packet.traderId(),
                    packet.translationKey()
            );
        });
    }
}
