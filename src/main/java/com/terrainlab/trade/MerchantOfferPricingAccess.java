package com.terrainlab.trade;

/**
 * Added to {@code MerchantOffer} by MerchantOfferPricingMixin: remembers an offer's original result
 * count while {@link PairPricing} has changed it for the current trading player.
 */
public interface MerchantOfferPricingAccess {

    /** Original result count, or -1 when the result is unchanged. */
    int terrainlab$getBaseResultCount();

    void terrainlab$setBaseResultCount(int count);
}
