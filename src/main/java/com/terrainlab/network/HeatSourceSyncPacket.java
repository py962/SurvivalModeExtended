package com.terrainlab.network;

import com.terrainlab.temperature.HeatChunks;
import com.terrainlab.temperature.HeatSourceData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: one indexed heat source (lit campfire or player-placed lava; see
 * HeatSourceIndexHandler).
 * <p>
 * The server index is a ServerLevel attachment, so client-side temperature
 * readers (HUD, F3 line, thermometer) rely on ClientHeatSourceIndex, kept in
 * sync by this packet and HeatSourceRemovePacket via HeatSourceSyncService.
 * Sources are sent per chunk as the client loads them. {@code enclosureFactor}
 * may be NaN ("not computed yet"); each side then computes it from its own
 * copy of the blocks, which yields the same value.
 */
public record HeatSourceSyncPacket(
        BlockPos sourcePos,
        double baseStrength,
        double enclosureFactor
) implements CustomPacketPayload {

    public static final Type<HeatSourceSyncPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "heat_source_sync"));

    public static final StreamCodec<FriendlyByteBuf, HeatSourceSyncPacket> STREAM_CODEC =
            StreamCodec.of(HeatSourceSyncPacket::write, HeatSourceSyncPacket::read);

    private static void write(FriendlyByteBuf buf, HeatSourceSyncPacket packet) {
        buf.writeBlockPos(packet.sourcePos);
        buf.writeDouble(packet.baseStrength);
        buf.writeDouble(packet.enclosureFactor);
    }

    private static HeatSourceSyncPacket read(FriendlyByteBuf buf) {
        BlockPos sourcePos = buf.readBlockPos();
        double baseStrength = buf.readDouble();
        double enclosureFactor = buf.readDouble();
        return new HeatSourceSyncPacket(sourcePos, baseStrength, enclosureFactor);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HeatSourceSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Ignore sources for chunks the client doesn't have; they are re-sent with the chunk.
            if (context.player() == null
                    || !HeatChunks.isLoaded(context.player().level(), packet.sourcePos())) {
                return;
            }
            HeatSourceData data = new HeatSourceData(
                    packet.sourcePos(), packet.baseStrength(), packet.enclosureFactor());
            com.terrainlab.client.ClientHeatSourceIndex.get().put(packet.sourcePos(), data);
        });
    }
}
