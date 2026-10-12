package com.terrainlab.network;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: raw use-key state for drinking from water with an empty hand. The
 * server performs all validation and hold/cooldown timing.
 */
public record WaterDrinkInputPacket(boolean keyDown) implements CustomPacketPayload {

    public static final Type<WaterDrinkInputPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "water_drink_input"));

    public static final StreamCodec<FriendlyByteBuf, WaterDrinkInputPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeBoolean(packet.keyDown),
                    buf -> new WaterDrinkInputPacket(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WaterDrinkInputPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                player.setData(ModAttachments.PLAYER_WANTS_WATER_DRINK.get(), packet.keyDown());
            }
        });
    }
}
