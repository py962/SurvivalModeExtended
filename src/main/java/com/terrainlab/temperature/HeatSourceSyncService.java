package com.terrainlab.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-to-client sync of the heat source index, per chunk: a chunk's sources are sent with the chunk
 * ({@link #sendChunkTo}, on ChunkWatchEvent.Sent) and changes go to players tracking that chunk. The client
 * mirror (ClientHeatSourceIndex) drops a chunk's sources when it unloads. Packets carry position, strength
 * and enclosure only.
 */
public final class HeatSourceSyncService {

    private HeatSourceSyncService() {
    }

    public static void broadcastUpdate(ServerLevel level, HeatSourceData data) {
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(data.sourcePos()), toSyncPacket(data));
    }

    public static void broadcastRemoval(ServerLevel level, BlockPos sourcePos) {
        PacketDistributor.sendToPlayersTrackingChunk(level, ChunkPos.containing(sourcePos),
                new com.terrainlab.network.HeatSourceRemovePacket(sourcePos));
    }

    /** Sends one chunk's indexed sources to a player who has just been sent that chunk. */
    public static void sendChunkTo(ServerPlayer player, HeatSourceIndex index, ChunkPos chunkPos) {
        for (HeatSourceData data : index.inChunk(chunkPos.x(), chunkPos.z())) {
            PacketDistributor.sendToPlayer(player, toSyncPacket(data));
        }
    }

    /** Sends every indexed source to one player. Only used by the unregistered HeatSourceIndexSyncHandler. */
    public static void sendAllTo(ServerPlayer player, HeatSourceIndex index) {
        for (HeatSourceData data : index.all().values()) {
            PacketDistributor.sendToPlayer(player, toSyncPacket(data));
        }
    }

    private static com.terrainlab.network.HeatSourceSyncPacket toSyncPacket(HeatSourceData data) {
        return new com.terrainlab.network.HeatSourceSyncPacket(
                data.sourcePos(), data.baseStrength(), data.enclosureFactor());
    }
}
