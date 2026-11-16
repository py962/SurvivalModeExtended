package com.terrainlab.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camels and elephants (ElephantEntity extends Camel) are tall enough to step over fences, fence gates
 * and walls one block high: for them such a single barrier has no collision, so they walk straight
 * through instead of stepping up (step height 1.5) and walking along the top. Barriers stacked two or
 * more high keep their normal collision and stop them. Glass panes and iron bars (the other
 * CrossCollisionBlocks) are unaffected.
 */
@Mixin({CrossCollisionBlock.class, WallBlock.class, FenceGateBlock.class})
public abstract class CamelStepOverFencesMixin {

    /** Fences, fence gates and walls. Stacked two or more high, they stop camels as normal. */
    private static boolean isBarrier(BlockState state) {
        return state.getBlock() instanceof FenceBlock || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof FenceGateBlock;
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$camelsStepOver(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                           CallbackInfoReturnable<VoxelShape> cir) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Camel) {
            Object self = this;
            if ((self instanceof FenceBlock || self instanceof WallBlock || self instanceof FenceGateBlock)
                    && !isBarrier(level.getBlockState(pos.above())) && !isBarrier(level.getBlockState(pos.below()))) {
                cir.setReturnValue(Shapes.empty()); // a single-height barrier: stepped over
            }
        }
    }
}
