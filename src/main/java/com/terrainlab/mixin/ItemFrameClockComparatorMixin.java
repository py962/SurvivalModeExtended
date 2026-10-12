package com.terrainlab.mixin;

import com.terrainlab.api.ClockFrameAccess;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes an item frame holding a Clock output a flat 0/15 comparator signal
 * driven by {@code terrainlab$clockPulseHigh}; other contents keep vanilla output.
 * <p>
 * ItemFrameRedstoneHandler toggles the flag via {@link ClockFrameAccess} and
 * notifies comparators itself.
 */
@Mixin(ItemFrame.class)
public abstract class ItemFrameClockComparatorMixin implements ClockFrameAccess {

    @Unique
    private boolean terrainlab$clockPulseHigh = false;

    @Override
    public boolean terrainlab$isClockPulseHigh() {
        return terrainlab$clockPulseHigh;
    }

    @Override
    public void terrainlab$setClockPulseHigh(boolean high) {
        terrainlab$clockPulseHigh = high;
    }

    @Inject(method = "getAnalogOutput", at = @At("HEAD"), cancellable = true)
    private void terrainlab$overrideClockOutput(CallbackInfoReturnable<Integer> cir) {
        ItemFrame self = (ItemFrame) (Object) this;
        ItemStack held = self.getItem();
        if (held.isEmpty() || !held.is(Items.CLOCK)) {
            return;
        }
        cir.setReturnValue(terrainlab$clockPulseHigh ? 15 : 0);
    }
}
