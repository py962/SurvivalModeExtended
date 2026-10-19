package com.terrainlab.mixin;

import com.terrainlab.entity.CaribouEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.Llama;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Caribou extend {@link Llama}, so vanilla's {@code Llama#canMate} (partner instanceof Llama) would let
 * llamas and caribou breed with each other. Caribou only mate with caribou, and llamas never mate with
 * caribou.
 */
@Mixin(Llama.class)
public abstract class LlamaCaribouMateMixin {

    @Inject(method = "canMate", at = @At("HEAD"), cancellable = true)
    private void terrainlab$noCaribouCrossbreeding(Animal partner, CallbackInfoReturnable<Boolean> cir) {
        boolean selfCaribou = (Object) this instanceof CaribouEntity;
        boolean partnerCaribou = partner instanceof CaribouEntity;
        if (selfCaribou != partnerCaribou) {
            cir.setReturnValue(false);
        }
    }
}
