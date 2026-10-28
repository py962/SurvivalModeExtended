package com.terrainlab.mixin;

import com.terrainlab.client.DebugViewGate;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Without cheats, the F3 world overlays never render, and their F3+key shortcuts (F3+B, F3+G) do
 * not switch them on. Their saved settings are left alone, so they come back with cheats.
 */
@Mixin(DebugScreenEntryList.class)
public abstract class DebugScreenEntryListGateMixin {

    @Inject(method = "isCurrentlyEnabled", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$gateOverlay(Identifier id, CallbackInfoReturnable<Boolean> cir) {
        if (DebugViewGate.blocked(id)) cir.setReturnValue(false);
    }

    @Inject(method = "toggleStatus", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$gateToggle(Identifier id, CallbackInfoReturnable<Boolean> cir) {
        if (DebugViewGate.blocked(id)) cir.setReturnValue(false);
    }
}
