package com.terrainlab.mixin;

import com.terrainlab.event.ModEndermanHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rejects teleports that would take a chorus-anchored enderman more than 32 blocks from its anchor. */
@Mixin(EnderMan.class)
public class EndermanTeleportMixin {

    @Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void limitAnchoredTeleport(double x, double y, double z,
                                       CallbackInfoReturnable<Boolean> cir) {
        EnderMan enderman = (EnderMan)(Object)this;
        BlockPos anchor = ModEndermanHandler.chorusAnchors.get(enderman.getUUID());
        if (anchor == null) return;

    // Horizontal distance only
        double distSq = (x - anchor.getX()) * (x - anchor.getX())
                + (z - anchor.getZ()) * (z - anchor.getZ());

        if (distSq > 32 * 32) {
            cir.setReturnValue(false);
        }
    }
}
