package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: a wood shield passively auto-blocked a hit. Sent to the blocker and its
 * trackers so clients briefly flash the shield's raised pose (purely visual).
 *
 * @param entityId the blocking entity's network id
 * @param mainHand true if the shield that blocked is in the main hand, false for the off hand
 */
public record ShieldAutoBlockPacket(int entityId, boolean mainHand) implements CustomPacketPayload {

    public static final Type<ShieldAutoBlockPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "shield_auto_block"));

    public static final StreamCodec<FriendlyByteBuf, ShieldAutoBlockPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.entityId);
                        buf.writeBoolean(packet.mainHand);
                    },
                    buf -> new ShieldAutoBlockPacket(buf.readVarInt(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShieldAutoBlockPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.terrainlab.client.ClientShieldAutoBlockState.flash(packet.entityId(), packet.mainHand()));
    }
}
