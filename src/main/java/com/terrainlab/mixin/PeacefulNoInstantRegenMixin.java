package com.terrainlab.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels {@code ServerPlayer#tickRegeneration} on Peaceful so health and food
 * behave as on Normal; that method heals and feeds directly, bypassing FoodData.
 */
@Mixin(ServerPlayer.class)
public abstract class PeacefulNoInstantRegenMixin {

    @Inject(method = "tickRegeneration()V", at = @At("HEAD"), cancellable = true)
    private void terrainlab$disablePeacefulInstantRegen(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.level().getDifficulty() == Difficulty.PEACEFUL) {
            ci.cancel();
        }
    }
}
