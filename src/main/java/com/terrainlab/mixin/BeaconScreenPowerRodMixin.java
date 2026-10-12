package com.terrainlab.mixin;

import com.terrainlab.weather.BeaconWeather;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client side of beacon weather control: disables every power button while a rod is in the
 * payment slot. Also covers BeaconUpgradePowerButton, which calls {@code super.updateStatus}.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconPowerButton")
public abstract class BeaconScreenPowerRodMixin {

    @Shadow @Final BeaconScreen this$0;

    @Inject(method = "updateStatus", at = @At("TAIL"), require = 1)
    private void terrainlab$dimForRod(int levels, CallbackInfo ci) {
        if (BeaconWeather.kindOf(this$0.getMenu().getSlot(0).getItem()) != null) {
            ((AbstractWidget) (Object) this).active = false;
        }
    }
}
