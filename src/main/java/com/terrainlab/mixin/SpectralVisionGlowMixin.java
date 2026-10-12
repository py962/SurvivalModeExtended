package com.terrainlab.mixin;

import com.terrainlab.effect.SpectralVisionMobEffect;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side Spectral Vision: while the local player has the effect, monsters within
 * {@link SpectralVisionMobEffect#RADIUS} render with the glowing outline on this client only.
 */
@Mixin(Minecraft.class)
public abstract class SpectralVisionGlowMixin {

    private static final double RADIUS_SQR = SpectralVisionMobEffect.RADIUS * SpectralVisionMobEffect.RADIUS;

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void terrainlab$spectralVision(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = ((Minecraft) (Object) this).player;
        if (player == null
                || entity == player
                || entity.getType().getCategory() != MobCategory.MONSTER
                || !player.hasEffect(ModMobEffects.SPECTRAL_VISION)
                || entity.distanceToSqr(player) > RADIUS_SQR) {
            return;
        }
        cir.setReturnValue(true);
    }
}
