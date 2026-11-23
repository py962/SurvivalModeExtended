package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A Phasing player's view goes dark inside blocks like anyone's, instead of seeing through them
 * as spectators do (vanilla skips the in-block overlay for any noPhysics player).
 */
@Mixin(LevelExtractor.class)
public abstract class PhasingViewOverlayMixin {

    @ModifyExpressionValue(
            method = "getViewBlockingStateAndPos",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;noPhysics:Z", opcode = Opcodes.GETFIELD),
            require = 1
    )
    private static boolean terrainlab$phasingStillBlocked(boolean noPhysics) {
        var player = Minecraft.getInstance().player;
        return noPhysics && !(player != null && player.hasEffect(ModMobEffects.PHASING));
    }
}
