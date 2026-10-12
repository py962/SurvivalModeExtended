package com.terrainlab.mixin;

import com.terrainlab.trade.MerchantOfferPricingAccess;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Holds an offer's original result count while reputation pricing has changed it. */
@Mixin(MerchantOffer.class)
public abstract class MerchantOfferPricingMixin implements MerchantOfferPricingAccess {

    @Unique
    private int terrainlab$baseResultCount = -1;

    @Override
    public int terrainlab$getBaseResultCount() {
        return this.terrainlab$baseResultCount;
    }

    @Override
    public void terrainlab$setBaseResultCount(int count) {
        this.terrainlab$baseResultCount = count;
    }
}
