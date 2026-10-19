package com.terrainlab.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.terrainlab.player.StatFlash;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Absorption granted by the effect (golden apples, Totem of Undying, /effect): flashes the hearts
 * gold when a player's absorption actually went up.
 */
@Mixin(targets = "net.minecraft.world.effect.AbsorptionMobEffect")
public abstract class AbsorptionStatFlashMixin {

    @Inject(method = "onEffectStarted", at = @At("HEAD"))
    private void terrainlab$before(LivingEntity mob, int amplifier, CallbackInfo ci,
                                   @Share("absorption") LocalFloatRef absorption) {
        absorption.set(mob.getAbsorptionAmount());
    }

    @Inject(method = "onEffectStarted", at = @At("RETURN"))
    private void terrainlab$after(LivingEntity mob, int amplifier, CallbackInfo ci,
                                  @Share("absorption") LocalFloatRef absorption) {
        if (mob instanceof ServerPlayer player && player.getAbsorptionAmount() > absorption.get()) {
            StatFlash.send(player, StatFlash.HEALTH);
        }
    }
}
