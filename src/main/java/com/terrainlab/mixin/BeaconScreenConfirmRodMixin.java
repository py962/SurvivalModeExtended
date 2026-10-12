package com.terrainlab.mixin;

import com.terrainlab.weather.BeaconWeather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client side of beacon weather control: with a rod in the payment slot, enables the confirm
 * button without a selected power when the weather can change, and shows the action or the
 * reason it is unavailable as its tooltip. Targets an inner class; the enclosing screen is
 * {@code this$0}.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconConfirmButton")
public abstract class BeaconScreenConfirmRodMixin {

    @Shadow @Final BeaconScreen this$0;

    @Inject(method = "updateStatus", at = @At("TAIL"), require = 1)
    private void terrainlab$rodConfirm(int levels, CallbackInfo ci) {
        AbstractWidget self = (AbstractWidget) (Object) this;
        var menu = this$0.getMenu();
        BeaconWeather.Kind kind = BeaconWeather.kindOf(menu.getSlot(0).getItem());
        if (kind == null) {
            self.setTooltip(Tooltip.create(CommonComponents.GUI_DONE));
            return;
        }
        var level = Minecraft.getInstance().level;
        Component problem = level == null ? Component.empty() : BeaconWeather.problem(level, kind, levels);
        self.active = problem == null;
        self.setTooltip(Tooltip.create(problem == null ? BeaconWeather.action(kind) : problem));
    }
}
