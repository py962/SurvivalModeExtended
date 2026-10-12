package com.terrainlab.mixin;

import com.terrainlab.trade.WanderingTraderTrades;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces {@code WanderingTrader#updateTrades} so offers are drawn from
 * {@link WanderingTraderTrades} pools with custom sample counts (20 sells, 2 buys);
 * vanilla's per-pool counts are fixed and not exposed by WandererTradesEvent.
 * <p>
 * The protected {@code addOffersFromItemListings} is reached through
 * {@link AbstractVillagerAccessor}, since {@code @Shadow} cannot see superclass members here.
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderSampleCountMixin {

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$customSampleCounts(ServerLevel level, CallbackInfo ci) {
        WanderingTrader self = (WanderingTrader) (Object) this;
        MerchantOffers offers = self.getOffers();
        AbstractVillagerAccessor accessor = (AbstractVillagerAccessor) (Object) this;
        accessor.terrainlab$invokeAddOffersFromItemListings(
                level, offers, WanderingTraderTrades.SELL_POOL.toArray(new VillagerTrades.ItemListing[0]), 20
        );
        accessor.terrainlab$invokeAddOffersFromItemListings(
                level, offers, WanderingTraderTrades.BUY_POOL.toArray(new VillagerTrades.ItemListing[0]), 2
        );
        ci.cancel();
    }
}
