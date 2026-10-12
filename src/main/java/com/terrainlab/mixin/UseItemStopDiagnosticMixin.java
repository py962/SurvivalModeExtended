package com.terrainlab.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic: logs each {@code LivingEntity#stopUsingItem} call with side, the
 * use item versus the item now in hand, remaining use ticks and a short caller
 * trace, to find what interrupts eating.
 * <p>
 * TODO: remove once the eating-interruption bug is confirmed fixed.
 */
@Mixin(LivingEntity.class)
public abstract class UseItemStopDiagnosticMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG = "[UseItemStopDiagnostic]";

    private static final boolean DEBUG_LOGGING = false;

    @Inject(method = "stopUsingItem", at = @At("HEAD"), require = 1)
    private void terrainlab$logStopUsingItem(CallbackInfo ci) {
        if (!DEBUG_LOGGING) return;

        LivingEntity self = (LivingEntity) (Object) this;

        String side = self.level().isClientSide() ? "CLIENT" : "SERVER";
        ItemStack capturedAtStart = self.getUseItem();
        ItemStack currentlyHeld;
        try {
            currentlyHeld = self.getItemInHand(self.getUsedItemHand());
        } catch (Exception ex) {
            currentlyHeld = ItemStack.EMPTY;
        }
        boolean sameItem = ItemStack.isSameItem(capturedAtStart, currentlyHeld);
        boolean sameItemSameComponents = ItemStack.isSameItemSameComponents(capturedAtStart, currentlyHeld);
        int remainingTicks = self.getUseItemRemainingTicks();
        long gameTime = self.level().getGameTime();

        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        StringBuilder callSite = new StringBuilder();
        // [0] is getStackTrace and [1] this handler; callers start at [2].
        for (int i = 2; i < Math.min(trace.length, 6); i++) {
            callSite.append(trace[i]).append(" <- ");
        }

        // LOGGER.info("{} side={} entity={} gameTime={} capturedAtStart={} currentlyHeld={} sameItem={} "
                        // + "sameItemSameComponents={} remainingTicksAtStop={} callSite={}",
                // TAG, side, self.getName().getString(), gameTime, capturedAtStart, currentlyHeld,
                // sameItem, sameItemSameComponents, remainingTicks, callSite);
    }
}
