package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: which Celestial Death respawn options are available for the death that just happened.
 * Sent from the LivingDeathEvent handler, i.e. before vanilla's ClientboundPlayerCombatKillPacket
 * opens the DeathScreen; the client still copes with either order.
 */
public record DeathOptionsPacket(boolean conduit, boolean pick) implements CustomPacketPayload {

    public static final Type<DeathOptionsPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "death_options"));

    public static final StreamCodec<FriendlyByteBuf, DeathOptionsPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBoolean(packet.conduit);
                        buf.writeBoolean(packet.pick);
                    },
                    buf -> new DeathOptionsPacket(buf.readBoolean(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DeathOptionsPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
                com.terrainlab.client.CelestialDeathScreenHandler.onOptionsReceived(packet.conduit(), packet.pick()));
    }
}
