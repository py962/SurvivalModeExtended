package com.terrainlab.network;

import com.terrainlab.advancement.AccomplishmentCard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: show the accomplishment card. Sent when an End return newly earns an accomplishment and
 * again for /accomplishments. The client queues it until no other screen (e.g. the credits) is open.
 */
public record AccomplishmentCardPacket(AccomplishmentCard card) implements CustomPacketPayload {

    public static final Type<AccomplishmentCardPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "accomplishment_card"));

    public static final StreamCodec<FriendlyByteBuf, AccomplishmentCardPacket> STREAM_CODEC =
            AccomplishmentCard.STREAM_CODEC.map(AccomplishmentCardPacket::new, AccomplishmentCardPacket::card);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AccomplishmentCardPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> com.terrainlab.client.AccomplishmentCardClient.queue(packet.card()));
    }
}
