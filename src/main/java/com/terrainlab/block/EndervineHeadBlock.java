package com.terrainlab.block;

import com.terrainlab.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Growing tip of an endervine. On random tick, with a player nearby, it extends one block down or
 * sideways into air, leaving a body segment behind. LENGTH counts segments grown, so a vine
 * stops at {@link #MAX_LENGTH} + 1 blocks.
 */
public class EndervineHeadBlock extends Block {

    public static final EnumProperty<Direction> FACING =
            BlockStateProperties.FACING;

    public static final int MAX_LENGTH = 31;
    public static final IntegerProperty LENGTH = IntegerProperty.create("length", 0, MAX_LENGTH);

    private static final double PLAYER_RANGE = 8.0; // blocks

    public EndervineHeadBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.DOWN)
                .setValue(LENGTH, 0));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LENGTH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return EndervineBodyBlock.tubeShape(state.getValue(FACING));
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(LENGTH) < MAX_LENGTH;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level,
                              BlockPos pos, RandomSource random) {
        boolean playerNearby = level.players().stream()
                .filter(p -> !p.isSpectator())
                .anyMatch(p -> p.distanceToSqr(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                        < PLAYER_RANGE * PLAYER_RANGE);

        if (!playerNearby || state.getValue(LENGTH) >= MAX_LENGTH) return;

        Direction[] directions = {
                Direction.DOWN,
                Direction.NORTH,
                Direction.SOUTH,
                Direction.EAST,
                Direction.WEST
        };
        Direction dir = directions[random.nextInt(directions.length)];
        BlockPos target = pos.relative(dir);

        if (!level.getBlockState(target).isAir()) return;

        level.setBlock(pos,
                ModBlocks.ENDERVINE_BODY.get().defaultBlockState()
                        .setValue(EndervineBodyBlock.FACING, dir), 3);
        level.setBlock(target,
                ModBlocks.ENDERVINE_HEAD.get().defaultBlockState()
                        .setValue(FACING, dir)
                        .setValue(LENGTH, state.getValue(LENGTH) + 1), 3);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getClickedFace().getOpposite());
    }
}
