package com.terrainlab.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the local player's first-person hands and held item while borrowing another entity's
 * view. Borrowing is detected client-side as the camera entity differing from the local player.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class BorrowingHideArmMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void terrainlab$hideArmWhileBorrowing(
            float partialTick,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            LocalPlayer player,
            int combinedLight,
            CallbackInfo ci) {

        if (Minecraft.getInstance().getCameraEntity() != player) {
            ci.cancel();
        }
    }
}
