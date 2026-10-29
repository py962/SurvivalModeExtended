package com.terrainlab.forestfloor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Seasonal fallen leaves: 1-8 layers like snow, but with no collision, so you wade
 * through them. Rustles and throws up tinted leaf particles when walked through.
 */
public class FallenLeavesBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    public static final EnumProperty<LeafSource> TREE = EnumProperty.create("tree", LeafSource.class);
    private static final VoxelShape[] SHAPES = Block.boxes(8, h -> Block.column(16.0, 0.0, h * 2));

    public FallenLeavesBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1).setValue(TREE, LeafSource.OAK));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, TREE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return !belowState.is(Blocks.ICE) && !belowState.is(Blocks.PACKED_ICE) && !belowState.is(Blocks.BARRIER)
                && belowState.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        double speedSq = entity.getDeltaMovement().horizontalDistanceSqr();
        if (speedSq < 1.0E-4) return;
        RandomSource random = level.getRandom();
        if (level.isClientSide()) {
            if (random.nextInt(3) == 0) {
                int layers = state.getValue(LAYERS);
                ColorParticleOption particle = ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, state.getValue(TREE).color);
                level.addParticle(particle,
                        entity.getX() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
                        pos.getY() + layers * 0.125 + 0.05,
                        entity.getZ() + (random.nextDouble() - 0.5) * entity.getBbWidth(),
                        (random.nextDouble() - 0.5) * 0.1, 0.08 + random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.1);
            }
        } else if (entity.tickCount % 8 == 0) {
            level.playSound(null, pos, SoundEvents.LEAF_LITTER_STEP, SoundSource.BLOCKS,
                    0.35F + state.getValue(LAYERS) * 0.05F, 0.8F + random.nextFloat() * 0.3F);
        }
    }
}
