package com.terrainlab.mixin;

import com.terrainlab.entity.AbstractTrailTraderEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Enforces Trail Trader relic requirements that vanilla offers cannot express (enchanted book type,
 * found-in-world maps). After updateSellItem fills the result slot, AbstractTrailTraderEntity#acceptsPayment
 * may reject the payment, which clears the result; it may also record the payment so the exact relic can be
 * returned later.
 * <p>
 * Server side only: the client container holds a ClientSideMerchant. Rejections are pushed to the client
 * immediately so it does not show a result that cannot be taken.
 */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerTrailTraderMixin {

    @Shadow
    @Final
    private Merchant merchant;

    @Shadow
    @Final
    private NonNullList<ItemStack> itemStacks;

    @Shadow
    @Nullable
    private MerchantOffer activeOffer;

    @Inject(method = "updateSellItem", at = @At("TAIL"), require = 1)
    private void terrainlab$checkGuideRelicPayment(CallbackInfo ci) {
        if (!(this.merchant instanceof AbstractTrailTraderEntity guide)
                || guide.level().isClientSide()) {

            return;
        }

        boolean resultShown = !this.itemStacks.get(2).isEmpty();

        if (guide.acceptsPayment(
                resultShown ? this.activeOffer : null,
                this.itemStacks.get(0),
                this.itemStacks.get(1))) {

            return;
        }

        this.itemStacks.set(2, ItemStack.EMPTY);
        this.activeOffer = null;

        if (guide.getTradingPlayer() instanceof ServerPlayer player) {
            player.containerMenu.sendAllDataToRemote();
        }
    }
}
