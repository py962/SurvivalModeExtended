package com.terrainlab.mixin;

import com.terrainlab.celestial.ShieldFireSuppression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fire and soul fire placed or spread inside an active Shield conduit's range go out at once.
 * Runs after vanilla's onPlace, so a nether portal lit with it still forms (the fire is already
 * replaced by the portal and the check finds no fire).
 */
@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockShieldMixin {

    @Inject(method = "onPlace", at = @At("RETURN"))
    private void terrainlab$shieldPutsOutNewFire(BlockState state, Level level, BlockPos pos, BlockState oldState,
                                                boolean movedByPiston, CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            ShieldFireSuppression.extinguishIfShielded(serverLevel, pos);
        }
    }
}
