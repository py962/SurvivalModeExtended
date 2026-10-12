package com.terrainlab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Deep Dark cave flower that must touch a sculk-family block (sculk, vein, catalyst, sensor or
 * shrieker); the same set as the sculk_flower_patch placement predicate.
 *
 * <p>TODO: sculk particle/sound ambiance, and decide whether a decayed variant is wanted.
 */
public class SculkFlowerBlock extends Block {

    private static final VoxelShape SHAPE = Shapes.box(0.1, 0.0, 0.1, 0.9, 1.0, 0.9);

    public SculkFlowerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // No properties.
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Pops off when its last sculk neighbour is removed. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.is(Blocks.SCULK)
                    || neighbor.is(Blocks.SCULK_VEIN)
                    || neighbor.is(Blocks.SCULK_CATALYST)
                    || neighbor.is(Blocks.SCULK_SENSOR)
                    || neighbor.is(Blocks.SCULK_SHRIEKER)) {
                return true;
            }
        }
        return false;
    }
}
