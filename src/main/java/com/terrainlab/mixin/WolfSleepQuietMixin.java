package com.terrainlab.mixin;

import com.terrainlab.pet.WolfCarpetSleep;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No ambient barks, pants or whines from a wolf asleep on a carpet (WolfCarpetSleepHandler). */
@Mixin(Wolf.class)
public abstract class WolfSleepQuietMixin {

    @Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$quietWhileAsleep(CallbackInfoReturnable<SoundEvent> cir) {
        if (WolfCarpetSleep.isAsleep(this)) {
            cir.setReturnValue(null);
        }
    }
}
