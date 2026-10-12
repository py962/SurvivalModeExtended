package com.terrainlab.network;

import com.terrainlab.TerrainLab;
import com.terrainlab.menu.AncientCodexMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S: the player clicked an Ancient Codex glyph. */
public record GlyphSelectionPacket(int glyphIndex) implements CustomPacketPayload {

    public static final Type<GlyphSelectionPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "glyph_selection"));

    public static final StreamCodec<FriendlyByteBuf, GlyphSelectionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeInt(packet.glyphIndex),
                    buf -> new GlyphSelectionPacket(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GlyphSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // TerrainLab.LOGGER.info("Glyph packet received: index {}", packet.glyphIndex());
                if (player.containerMenu instanceof AncientCodexMenu menu && menu.stillValid(player)) {
                    menu.handleGlyphSelection(player, packet.glyphIndex());
                } else {
                    // TerrainLab.LOGGER.info("Player menu is not AncientCodexMenu: {}",
                            // player.containerMenu.getClass().getName());
                }
            }
        });
    }
}
