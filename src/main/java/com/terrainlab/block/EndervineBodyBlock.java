package com.terrainlab.block;

import com.terrainlab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Grown segment of an endervine, a tube along its growth direction; placing it by hand places a vine head instead. */
public class EndervineBodyBlock extends Block {

    public static final EnumProperty<Direction> FACING =
            BlockStateProperties.FACING;

    public EndervineBodyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.DOWN));
    }

    // A 6-pixel-wide tube along the growth axis.
    private static final VoxelShape SHAPE_Y = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape SHAPE_X = Block.box(0, 5, 5, 16, 11, 11);
    private static final VoxelShape SHAPE_Z = Block.box(5, 5, 0, 11, 11, 16);

    static VoxelShape tubeShape(Direction facing) {
        return switch (facing.getAxis()) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return tubeShape(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return ModBlocks.ENDERVINE_HEAD.get().defaultBlockState()
                .setValue(EndervineHeadBlock.FACING,
                        context.getClickedFace().getOpposite());
    }
}
