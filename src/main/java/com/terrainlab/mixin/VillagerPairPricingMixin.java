package com.terrainlab.mixin;

import com.terrainlab.trade.PairPricing;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's per-player villager price adjustment with {@link PairPricing}, restores base
 * prices when trading stops, and keeps changed result counts out of saved data.
 */
@Mixin(Villager.class)
public abstract class VillagerPairPricingMixin {

    @Inject(method = "updateSpecialPrices", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$pairPricing(Player player, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        PairPricing.apply(self.getOffers(), PairPricing.effectiveReputation(self, player));
        ci.cancel();
    }

    @Inject(method = "resetSpecialPrices", at = @At("TAIL"), require = 1)
    private void terrainlab$restoreResults(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (!self.level().isClientSide()) {
            PairPricing.restoreResults(self.getOffers());
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"), require = 1)
    private void terrainlab$saveBasePrices(ValueOutput output, CallbackInfo ci) {
        PairPricing.restore(((Villager) (Object) this).getOffers());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"), require = 1)
    private void terrainlab$reapplyAfterSave(ValueOutput output, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        Player trading = self.getTradingPlayer();
        if (trading != null) {
            PairPricing.apply(self.getOffers(), PairPricing.effectiveReputation(self, trading));
        }
    }
}
