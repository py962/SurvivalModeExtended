package com.terrainlab.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * World spawn and player spawn placement: never on leaves, never in deep water.
 * <p>
 * {@code PlayerSpawnFinder#getLevelRespawnPos} is the one column test behind both choosing the
 * world spawn when a world is created ({@code getSpawnPosInChunk}) and placing a player within the
 * spawn radius (first join, respawn without a bed, every new character). Vanilla stands the player
 * on the first block with a full top face, which includes leaves (tree tops). In dimensions without
 * a ceiling this replaces it:
 * <ul>
 *   <li>leaves are looked through, so under trees the player stands on the forest floor;</li>
 *   <li>water one block deep (feet in the water, head clear) is fine; deeper water, and lava,
 *       reject the column;</li>
 *   <li>the floor must have a full top face and there must be room to stand (no solid block at the
 *       feet or head), so a column through a tree trunk is rejected rather than standing the player
 *       on the trunk inside the canopy.</li>
 * </ul>
 * {@code noCollisionNoLiquid}, the final check on a chosen spot, treats any fluid as solid; it is
 * relaxed for exactly the shallow case so a one-deep spot found above is not thrown away.
 * Dimensions with a ceiling (the Nether) keep vanilla's behaviour.
 */
@Mixin(PlayerSpawnFinder.class)
public abstract class PlayerSpawnFinderSafeGroundMixin {

    @Shadow @Final private static EntityDimensions PLAYER_DIMENSIONS;

    @Inject(method = "getLevelRespawnPos", at = @At("HEAD"), cancellable = true)
    private static void terrainlab$safeGround(ServerLevel level, int x, int z, CallbackInfoReturnable<BlockPos> cir) {
        if (level.dimensionType().hasCeiling()) return;
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        BlockPos result = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top + 1; y >= level.getMinY(); y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) {
                // Water at the feet only, over a real floor: shallow, allowed. Anything else: deep water or lava.
                BlockPos.MutableBlockPos below = pos.mutable().move(Direction.DOWN);
                BlockState floor = level.getBlockState(below);
                if (state.getFluidState().is(FluidTags.WATER)
                        && level.getFluidState(pos.above()).isEmpty()
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                        && isStandable(level, below, floor)) {
                    result = pos.immutable();
                }
                break;
            }
            if (state.is(BlockTags.LEAVES)) continue;
            if (isStandable(level, pos, state)) {
                BlockPos feet = pos.above();
                BlockState feetState = level.getBlockState(feet);
                BlockState headState = level.getBlockState(feet.above());
                if (feetState.getCollisionShape(level, feet).isEmpty() && feetState.getFluidState().isEmpty()
                        && headState.getCollisionShape(level, feet.above()).isEmpty() && headState.getFluidState().isEmpty()) {
                    result = feet.immutable();
                }
                break;
            }
        }
        cir.setReturnValue(result);
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.is(BlockTags.LEAVES) && state.getFluidState().isEmpty()
                && Block.isFaceFull(state.getCollisionShape(level, pos), Direction.UP);
    }

    @Inject(method = "noCollisionNoLiquid", at = @At("HEAD"), cancellable = true)
    private static void terrainlab$allowShallowWater(CollisionGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.above()).isEmpty()
                && level.noCollision(null, PLAYER_DIMENSIONS.makeBoundingBox(Vec3.atBottomCenterOf(pos)), false)) {
            cir.setReturnValue(true);
        }
    }
}
