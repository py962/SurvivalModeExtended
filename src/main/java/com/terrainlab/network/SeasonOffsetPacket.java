package com.terrainlab.network;

import com.terrainlab.season.Season;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: the world's seasonal offset and year length (SeasonSavedData), which is server-only
 * SavedData, for client-side season code.
 */
public record SeasonOffsetPacket(long seasonalOffset, int yearLengthDays) implements CustomPacketPayload {

    public static final Type<SeasonOffsetPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "season_offset"));

    public static final StreamCodec<FriendlyByteBuf, SeasonOffsetPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeLong(packet.seasonalOffset);
                        buf.writeVarInt(packet.yearLengthDays);
                    },
                    buf -> new SeasonOffsetPacket(buf.readLong(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SeasonOffsetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Season.setYearLengthDays(packet.yearLengthDays());
            com.terrainlab.client.ClientSeasonState.setSeasonalOffset(packet.seasonalOffset());
        });
    }
}
