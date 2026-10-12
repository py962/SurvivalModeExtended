package com.terrainlab.mixin;

import com.terrainlab.event.HazardOwnerTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Charges lava placed by a dispenser to whoever placed the dispenser: compares the block in front
 * before and after the dispense, and records a new lava source with the dispenser's owner.
 */
@Mixin(DispenserBlock.class)
public abstract class DispenserLavaOwnerMixin {

    /** Whether the target was already a lava source; dispensing runs on the server thread only. */
    @Unique
    private static boolean terrainlab$targetWasLava;

    @Inject(method = "dispenseFrom", at = @At("HEAD"), require = 1)
    private void terrainlab$beforeDispense(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
        BlockPos target = pos.relative(state.getValue(DispenserBlock.FACING));
        terrainlab$targetWasLava = HazardOwnerTracker.isLavaSourceAt(level, target);
    }

    @Inject(method = "dispenseFrom", at = @At("RETURN"), require = 1)
    private void terrainlab$afterDispense(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
        BlockPos target = pos.relative(state.getValue(DispenserBlock.FACING));
        if (!terrainlab$targetWasLava && HazardOwnerTracker.isLavaSourceAt(level, target)) {
            HazardOwnerTracker.onDispenserPlacedLava(level, pos, target);
        }
    }
}
