package com.terrainlab.mixin;

import com.terrainlab.entity.CaribouEntity;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla equines (caribou extend Llama) only breed when tamed. A wild caribou a village butcher put in
 * love ({@link ModAttachments#CARIBOU_VILLAGE_LOVE}) may breed untamed while that love lasts (no
 * full-health requirement either); the flag is dropped once the love ends.
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseVillageLoveMixin {

    @Inject(method = "canParent", at = @At("HEAD"), cancellable = true)
    private void terrainlab$villageLove(CallbackInfoReturnable<Boolean> cir) {
        AbstractHorse self = (AbstractHorse) (Object) this;
        if (!(self instanceof CaribouEntity) || self.isTamed()
                || !self.hasData(ModAttachments.CARIBOU_VILLAGE_LOVE.get())) {
            return;
        }
        if (!self.isInLove()) {
            self.removeData(ModAttachments.CARIBOU_VILLAGE_LOVE.get());
            return;
        }
        if (self.getData(ModAttachments.CARIBOU_VILLAGE_LOVE.get())) {
            cir.setReturnValue(!self.isVehicle() && !self.isPassenger() && !self.isBaby());
        }
    }
}
