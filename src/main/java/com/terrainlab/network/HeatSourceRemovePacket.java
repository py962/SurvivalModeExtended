package com.terrainlab.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: a previously synced heat source no longer qualifies (broken, doused or
 * extinguished); ClientHeatSourceIndex drops it.
 */
public record HeatSourceRemovePacket(BlockPos sourcePos) implements CustomPacketPayload {

    public static final Type<HeatSourceRemovePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "heat_source_remove"));

    public static final StreamCodec<FriendlyByteBuf, HeatSourceRemovePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBlockPos(packet.sourcePos()),
                    buf -> new HeatSourceRemovePacket(buf.readBlockPos())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeatSourceRemovePacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.terrainlab.client.ClientHeatSourceIndex.get().remove(packet.sourcePos())
        );
    }
}
