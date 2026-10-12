package com.terrainlab.network;

import com.terrainlab.celestial.DeathRespawnManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S marker: the player confirmed their spectator-camera position as the
 * chosen respawn point (attack key while the picking prompt is shown).
 */
public record DeathConfirmPickingPacket() implements CustomPacketPayload {

    public static final Type<DeathConfirmPickingPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "death_confirm_picking"));

    public static final StreamCodec<FriendlyByteBuf, DeathConfirmPickingPacket> STREAM_CODEC =
            StreamCodec.unit(new DeathConfirmPickingPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeathConfirmPickingPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                DeathRespawnManager.requestConfirm(player.getUUID());
            }
        });
    }
}
