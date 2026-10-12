package com.terrainlab.network;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: raw sprint-key state while mounted. Vanilla sends no sprint-start
 * packet while riding, so {@code isSprinting()} cannot be used server-side.
 */
public record HorseSprintInputPacket(boolean wantsSprint) implements CustomPacketPayload {

    public static final Type<HorseSprintInputPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "horse_sprint_input"));

    public static final StreamCodec<FriendlyByteBuf, HorseSprintInputPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBoolean(packet.wantsSprint),
                    buf -> new HorseSprintInputPacket(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HorseSprintInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.setData(ModAttachments.PLAYER_WANTS_HORSE_SPRINT.get(), packet.wantsSprint());
            }
        });
    }
}
