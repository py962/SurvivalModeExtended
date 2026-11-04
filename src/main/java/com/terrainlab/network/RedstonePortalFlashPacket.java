package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: the player has just travelled through a Redstone Portal, so the client plays the red
 * arrival flash (purely visual; see {@code RedstonePortalFlashLayer}). No payload.
 */
public record RedstonePortalFlashPacket() implements CustomPacketPayload {

    public static final RedstonePortalFlashPacket INSTANCE = new RedstonePortalFlashPacket();

    public static final Type<RedstonePortalFlashPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "redstone_portal_flash"));

    public static final StreamCodec<FriendlyByteBuf, RedstonePortalFlashPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RedstonePortalFlashPacket packet, IPayloadContext context) {
        context.enqueueWork(com.terrainlab.client.RedstonePortalFlashLayer::trigger);
    }
}
