package com.terrainlab.network;

import com.terrainlab.menu.ReplicatorMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: the Replicator GUI button was pressed. Runs the same
 * ReplicatorBlockEntity#trigger as a redstone rising edge.
 */
public record ReplicatorPulsePacket() implements CustomPacketPayload {

    public static final Type<ReplicatorPulsePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "replicator_pulse"));

    public static final StreamCodec<FriendlyByteBuf, ReplicatorPulsePacket> STREAM_CODEC =
            StreamCodec.unit(new ReplicatorPulsePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ReplicatorPulsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof ReplicatorMenu menu
                    && menu.stillValid(player)) {
                menu.getBlockEntity().trigger();
            }
        });
    }
}
