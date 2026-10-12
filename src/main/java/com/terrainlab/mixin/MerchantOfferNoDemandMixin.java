package com.terrainlab.mixin;

import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Trade prices never rise with demand; reputation-based price changes still apply. */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferNoDemandMixin {

    @Inject(method = "updateDemand", at = @At("HEAD"), cancellable = true)
    private void terrainlab$noDemand(CallbackInfo ci) {
        ci.cancel();
    }
}
