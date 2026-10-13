package com.terrainlab.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: result of an Ancient Codex 3-glyph attempt, sent from
 * AncientCodexMenu#tryProduceOutput to drive the screen's animation.
 *
 * @param success   true if the book/lapis/XP were consumed (glyphs fly into the output slot),
 *                  false if the attempt was refused (glyphs flicker and dim)
 * @param glyphMask bit i set = glyph i was part of the selection
 */
public record CodexResultPacket(boolean success, int glyphMask) implements CustomPacketPayload {

    public static final Type<CodexResultPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("terrainlab", "codex_result"));

    public static final StreamCodec<FriendlyByteBuf, CodexResultPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeBoolean(packet.success);
                        buf.writeVarInt(packet.glyphMask);
                    },
                    buf -> new CodexResultPacket(buf.readBoolean(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CodexResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen()
                    instanceof com.terrainlab.screen.AncientCodexScreen screen) {
                screen.onCodexResult(packet.success(), packet.glyphMask());
            }
        });
    }
}
