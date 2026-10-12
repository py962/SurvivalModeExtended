package com.terrainlab.mixin;

import com.terrainlab.celestial.ShieldFireSuppression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire that was already burning when a Shield conduit came into range goes out on its next fire
 * tick (every 1.5 to 2 seconds) instead of spreading.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockShieldMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void terrainlab$shieldPutsOutBurningFire(BlockState state, ServerLevel level, BlockPos pos,
                                                    RandomSource random, CallbackInfo ci) {
        if (ShieldFireSuppression.extinguishIfShielded(level, pos)) {
            ci.cancel();
        }
    }
}
