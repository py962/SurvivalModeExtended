package com.terrainlab.mixin;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.terrainlab.player.StatFlash;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Instant Health (potion drunk/splashed/lingering, tipped arrow, /effect): flashes the hearts gold
 * when it actually raised a player's health. Compares health before and after, so Instant Damage
 * and undead inversion never flash.
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealOrHarmStatFlashMixin {

    @Inject(method = "applyEffectTick", at = @At("HEAD"))
    private void terrainlab$tickBefore(ServerLevel level, LivingEntity mob, int amplification,
                                       CallbackInfoReturnable<Boolean> cir,
                                       @Share("health") LocalFloatRef health) {
        health.set(mob.getHealth());
    }

    @Inject(method = "applyEffectTick", at = @At("RETURN"))
    private void terrainlab$tickAfter(ServerLevel level, LivingEntity mob, int amplification,
                                      CallbackInfoReturnable<Boolean> cir,
                                      @Share("health") LocalFloatRef health) {
        if (mob instanceof ServerPlayer player && player.getHealth() > health.get()) {
            StatFlash.send(player, StatFlash.HEALTH);
        }
    }

    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"))
    private void terrainlab$instantBefore(ServerLevel level, Entity source, Entity owner, LivingEntity mob,
                                          int amplification, double scale, CallbackInfo ci,
                                          @Share("health") LocalFloatRef health) {
        health.set(mob.getHealth());
    }

    @Inject(method = "applyInstantenousEffect", at = @At("RETURN"))
    private void terrainlab$instantAfter(ServerLevel level, Entity source, Entity owner, LivingEntity mob,
                                         int amplification, double scale, CallbackInfo ci,
                                         @Share("health") LocalFloatRef health) {
        if (mob instanceof ServerPlayer player && player.getHealth() > health.get()) {
            StatFlash.send(player, StatFlash.HEALTH);
        }
    }
}
