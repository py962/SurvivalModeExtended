package com.terrainlab.network;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.ElephantEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S marker: the player wants to sleep in the howdah. Sent after client-side
 * checks; ElephantEntity.attemptHowdahSleep re-validates everything server-side.
 */
public record HowdahSleepRequestPacket() implements CustomPacketPayload {

    // Enables debug logging for this handler.
    private static final boolean DEBUG_LOGGING = false;

    public static final Type<HowdahSleepRequestPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "howdah_sleep_request"));

    public static final StreamCodec<FriendlyByteBuf, HowdahSleepRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new HowdahSleepRequestPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HowdahSleepRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (DEBUG_LOGGING) {
                TerrainLab.LOGGER.info("[HOWDAH_SERVER] packet received, player={}", context.player());
            }
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.getVehicle() instanceof ElephantEntity elephant) {
                if (DEBUG_LOGGING) {
                    TerrainLab.LOGGER.info("[HOWDAH_SERVER] calling attemptHowdahSleep");
                }
                elephant.attemptHowdahSleep(serverPlayer);
            } else {
                if (DEBUG_LOGGING) {
                    TerrainLab.LOGGER.info("[HOWDAH_SERVER] player not on elephant or not ServerPlayer - ignoring");
                }
            }
        });
    }
}
