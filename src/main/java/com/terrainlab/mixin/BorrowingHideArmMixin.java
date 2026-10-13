package com.terrainlab.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the local player's first-person hands and held item while borrowing another entity's
 * view. Borrowing is detected client-side as the camera entity differing from the local player.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class BorrowingHideArmMixin {

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void terrainlab$hideArmWhileBorrowing(
            float partialTick,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            PlayerRenderState playerState,
            FirstPersonHandsAndItemsRenderState state,
            CallbackInfo ci) {

        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != mc.player) {
            ci.cancel();
        }
    }
}
