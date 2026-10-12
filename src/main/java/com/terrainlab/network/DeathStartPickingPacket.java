package com.terrainlab.network;

import com.terrainlab.celestial.DeathRespawnManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S marker: the death screen's "Choose Respawn Location" button (Death amulet
 * only) was pressed. Same ordering guarantee as DeathConduitRespawnPacket.
 */
public record DeathStartPickingPacket() implements CustomPacketPayload {

    public static final Type<DeathStartPickingPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "death_start_picking"));

    public static final StreamCodec<FriendlyByteBuf, DeathStartPickingPacket> STREAM_CODEC =
            StreamCodec.unit(new DeathStartPickingPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeathStartPickingPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DeathRespawnManager.choosePickLocation(player.getUUID());
            }
        });
    }
}
