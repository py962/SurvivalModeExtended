package com.terrainlab.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Projectiles see fences and fence gates at their visual height (1 block) instead of the
 * 1.5-block jump-blocking collision height, so a player standing beside a fence can shoot
 * down over it. Walls keep their full collision height.
 */
@Mixin(ClipContext.class)
public abstract class ProjectileFenceClipMixin {
    @Shadow @Final private ClipContext.Block block;
    @Shadow @Final private CollisionContext collisionContext;

    @Inject(method = "getBlockShape", at = @At("RETURN"), cancellable = true)
    private void terrainlab$lowFencesForProjectiles(BlockState state, BlockGetter level, BlockPos pos,
                                                    CallbackInfoReturnable<VoxelShape> cir) {
        if (this.block != ClipContext.Block.COLLIDER) return;
        if (!(this.collisionContext instanceof EntityCollisionContext ctx)
                || !(ctx.getEntity() instanceof Projectile)) return;
        if (!state.is(BlockTags.FENCES) && !state.is(BlockTags.FENCE_GATES)) return;
        cir.setReturnValue(Shapes.join(cir.getReturnValue(), Shapes.block(), BooleanOp.AND));
    }
}
