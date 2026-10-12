package com.terrainlab.network;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: raw jump/attack key state for the energy system. The server does the
 * rising-edge detection in PlayerEnergySystem.
 */
public record EnergyInputPacket(boolean jumpDown, boolean attackDown) implements CustomPacketPayload {

    public static final Type<EnergyInputPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "energy_input"));

    public static final StreamCodec<FriendlyByteBuf, EnergyInputPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBoolean(packet.jumpDown);
                        buf.writeBoolean(packet.attackDown);
                    },
                    buf -> new EnergyInputPacket(buf.readBoolean(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EnergyInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.setData(ModAttachments.PLAYER_WANTS_JUMP.get(), packet.jumpDown());
                player.setData(ModAttachments.PLAYER_WANTS_ATTACK.get(), packet.attackDown());
            }
        });
    }
}
