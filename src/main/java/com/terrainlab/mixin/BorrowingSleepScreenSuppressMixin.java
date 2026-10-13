package com.terrainlab.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents any screen except the pause menu from opening while borrowing, keeping the player a
 * passive observer (including the in-bed chat screen). Borrowing is detected client-side as a
 * sleeping local player whose camera is another entity (a spectator watching a mob is not sleeping). The pause menu stays available so the player
 * can always quit.
 */
// 26.x: screen management moved from Minecraft#setScreen to Gui#setScreen.
@Mixin(Gui.class)
public abstract class BorrowingSleepScreenSuppressMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void terrainlab$suppressScreensWhileBorrowing(Screen screen, CallbackInfo ci) {
        Minecraft self = Minecraft.getInstance();

        if (screen != null
                && !(screen instanceof PauseScreen)
                && self.player != null
                && self.player.isSleeping()
                && self.getCameraEntity() != self.player) {
            ci.cancel();
        }
    }
}
