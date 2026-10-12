package com.terrainlab.mixin;

import com.terrainlab.season.SolarTime;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the bed sleep window on the unwarped vanilla clock (12542..23459, or any time during a
 * thunderstorm) in dimensions where seasonal day length warps the sun. Covers both entering and
 * staying in bed.
 */
@Mixin(BedRule.Rule.class)
public abstract class BedRuleVanillaSleepWindowMixin {

    @Inject(method = "test", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$vanillaSleepWindow(Level level, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == BedRule.Rule.WHEN_DARK && SolarTime.warps(level)) {
            cir.setReturnValue(SolarTime.isVanillaSleepTime(level) || level.isThundering());
        }
    }
}
