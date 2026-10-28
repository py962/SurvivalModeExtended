package com.terrainlab.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.terrainlab.client.VillagerFishingRodClient;
import net.minecraft.client.model.VillagerLikeModel;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HoldingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a fishing fisherman's rod in its right hand (see {@link VillagerFishingRodClient}) instead of
 * showing it in front of the crossed arms. The pose reproduces a player's third-person hand chain
 * (ItemInHandLayer: arm at the ITEM pose angle, then -90 X / 180 Y and the hand offset), anchored at the
 * end of the right forearm of the villager's arms part so it follows the arms; the item's own
 * third-person right-hand transforms (handheld_rod) then size and orient the rod. The rod is drawn only
 * once, by this layer.
 */
@Mixin(CrossedArmsItemLayer.class)
public abstract class CrossedArmsItemLayerFishingMixin {

    @Inject(
            method = "applyTranslation(Lnet/minecraft/client/renderer/entity/state/HoldingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void terrainlab$holdRod(HoldingEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
        if (!Boolean.TRUE.equals(state.getRenderData(VillagerFishingRodClient.FISHING))) {
            return;
        }
        VillagerLikeModel model = (VillagerLikeModel) ((RenderLayer) (Object) this).getParentModel();
        model.translateToArms(state, poseStack);
        // Right hand: the end of the right forearm box (x -8..-4, y -2..6, z -2..2 in the arms part).
        poseStack.translate(-6.0F / 16.0F, 6.0F / 16.0F, -2.0F / 16.0F);
        // Turn the arms part's frame into a held-item arm angle (relative to the body).
        poseStack.mulPose(Axis.XP.rotation(VillagerFishingRodClient.HOLD_ARM_X
                - VillagerFishingRodClient.ARMS_REST_X - VillagerFishingRodClient.ARMS_RAISE));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        ci.cancel();
    }
}
