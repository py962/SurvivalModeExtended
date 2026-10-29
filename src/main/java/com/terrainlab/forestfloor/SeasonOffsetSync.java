package com.terrainlab.forestfloor;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.SeasonSavedData;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Sends the world's seasonal offset to each client on login, so client-side visuals
 * (crimson petals) can work out the current season.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class SeasonOffsetSync {
    private SeasonOffsetSync() {}

    /** Last offset received from the server; Long.MIN_VALUE until one arrives. Client side only. */
    public static volatile long clientOffset = Long.MIN_VALUE;

    public record Payload(long offset) implements CustomPacketPayload {
        public static final Type<Payload> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(TerrainLab.MODID, "season_offset"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Payload> CODEC =
                ByteBufCodecs.VAR_LONG.map(Payload::new, Payload::offset);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Payload.TYPE, Payload.CODEC,
                (payload, context) -> clientOffset = payload.offset());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            long offset = SeasonSavedData.get(player.level().getServer().overworld()).getSeasonalOffset();
            PacketDistributor.sendToPlayer(player, new Payload(offset));
        }
    }
}
