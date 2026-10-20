package com.terrainlab.network;

import com.terrainlab.combat.DualWield;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: dual-wield off-hand strike (right-click). {@code entityId} is the struck entity, or
 * {@link #NO_TARGET} for a swing at nothing / a spear jab (which finds its own targets).
 * The server re-validates everything and uses its own off-hand ticker; see {@link DualWield}.
 */
public record OffhandStrikePacket(int entityId) implements CustomPacketPayload {

    public static final int NO_TARGET = -1;

    public static final Type<OffhandStrikePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "offhand_strike"));

    public static final StreamCodec<FriendlyByteBuf, OffhandStrikePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeVarInt(packet.entityId),
                    buf -> new OffhandStrikePacket(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OffhandStrikePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DualWield.handleStrike(player, packet.entityId());
            }
        });
    }
}
