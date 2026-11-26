package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.terrainlab.client.SpyglassViewBoost;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Single player: the built-in server's view distance follows the spyglass boost. */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerSpyglassViewMixin {

    @ModifyExpressionValue(method = "tickServer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
            require = 1)
    private Object terrainlab$scopedViewDistance(Object renderDistance) {
        return SpyglassViewBoost.apply((Integer) renderDistance);
    }
}
