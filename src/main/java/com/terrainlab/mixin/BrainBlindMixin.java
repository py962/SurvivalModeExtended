package com.terrainlab.mixin;

import com.terrainlab.event.MobEffectAiHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blind brain-driven mobs (piglins, goats, villagers...) skip their brain entirely. */
@Mixin(Brain.class)
public abstract class BrainBlindMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$skipWhenBlind(ServerLevel level, LivingEntity body, CallbackInfo ci) {
        if (body instanceof Mob mob && MobEffectAiHandler.isBlind(mob)) {
            ci.cancel();
        }
    }
}
