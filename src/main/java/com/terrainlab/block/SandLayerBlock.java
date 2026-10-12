package com.terrainlab.block;

import com.mojang.serialization.MapCodec;
import com.terrainlab.weather.Sandstorm;
import com.terrainlab.weather.WeatherConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Sand and red sand layers deposited by sandstorms. A copy of vanilla's SnowLayerBlock (same
 * layers, shapes, placement and survival rules) with two differences:
 * <ul>
 *   <li>no melting by light;</li>
 *   <li>wind erosion: in an arid biome, outside a storm and open to the sky, random ticks
 *       occasionally remove a layer. Covered layers and layers outside arid biomes never erode.</li>
 * </ul>
 * Copied rather than extended because SnowLayerBlock's codec is typed to SnowLayerBlock.
 */
public class SandLayerBlock extends Block {

    public static final MapCodec<SandLayerBlock> CODEC = simpleCodec(SandLayerBlock::new);
    public static final int MAX_HEIGHT = 8;
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    private static final VoxelShape[] SHAPES = Block.boxes(8, layers -> Block.column(16.0, 0.0, layers * 2));

    public SandLayerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    public MapCodec<SandLayerBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.LAND && state.getValue(LAYERS) < 5;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS) - 1];
    }

    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LAYERS) == MAX_HEIGHT ? 0.2F : 1.0F;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) {
            return false;
        }
        return below.is(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)
                || Block.isFaceFull(below.getCollisionShape(level, pos.below()), Direction.UP)
                || below.is(this) && below.getValue(LAYERS) == MAX_HEIGHT;
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction direction,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random) {

        return !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    /** Wind erosion. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(WeatherConstants.SAND_LAYER_EROSION_CHANCE) != 0
                || !Sandstorm.isSandstormBiome(level, pos)
                || Sandstorm.isActiveAt(level, pos)
                || !level.canSeeSky(pos.above())) {
            return;
        }

        int layers = state.getValue(LAYERS);
        if (layers <= 1) {
            level.removeBlock(pos, false);
        } else {
            level.setBlock(pos, state.setValue(LAYERS, layers - 1), Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        int layers = state.getValue(LAYERS);
        if (!context.getItemInHand().is(this.asItem()) || layers >= MAX_HEIGHT) {
            return layers == 1;
        }
        return !context.replacingClickedOnBlock() || context.getClickedFace() == Direction.UP;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) {
            return existing.setValue(LAYERS, Math.min(MAX_HEIGHT, existing.getValue(LAYERS) + 1));
        }
        return super.getStateForPlacement(context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }
}
