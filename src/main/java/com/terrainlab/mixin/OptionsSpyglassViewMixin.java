package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.terrainlab.client.SpyglassViewBoost;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Spyglass view distance: what the client draws and what it asks the server to send. */
@Mixin(Options.class)
public abstract class OptionsSpyglassViewMixin {

    @ModifyExpressionValue(method = "getEffectiveRenderDistance",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"),
            require = 1)
    private Object terrainlab$scopedDrawDistance(Object renderDistance) {
        return SpyglassViewBoost.apply((Integer) renderDistance);
    }

    @ModifyExpressionValue(method = "buildPlayerInformation",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
            require = 1)
    private Object terrainlab$scopedRequestedDistance(Object renderDistance) {
        return SpyglassViewBoost.apply((Integer) renderDistance);
    }
}
