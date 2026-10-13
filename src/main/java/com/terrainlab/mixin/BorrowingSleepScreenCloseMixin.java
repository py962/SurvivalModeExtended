package com.terrainlab.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Closes InBedChatScreen once the camera switches to the borrowed entity, covering the case where
 * the sleep screen opened before the camera packet arrived. Injects at TAIL so the new camera
 * entity is already applied.
 */
@Mixin(ClientPacketListener.class)
public abstract class BorrowingSleepScreenCloseMixin {

    @Inject(method = "handleSetCamera", at = @At("TAIL"))
    private void terrainlab$closeSleepScreenOnCameraDiverge(ClientboundSetCameraPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.gui.screen() instanceof InBedChatScreen
                && mc.player != null
                && mc.getCameraEntity() != mc.player) {
            mc.gui.setScreen(null);
        }
    }
}
