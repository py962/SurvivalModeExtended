package com.terrainlab.block;

import com.terrainlab.entity.VoidGlassFallingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Void glass: a falling block in reverse, rising when the space above it is free. */
public class VoidGlassBlock extends FallingBlock {

    public VoidGlassBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public int getDustColor(BlockState state, BlockGetter level, BlockPos pos) {
        return 0x3D0F5F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }


    // Replaces FallingBlock#tick entirely: checks upward and spawns a rising entity.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isFree(level.getBlockState(pos.above())) && pos.getY() <= level.getMaxY()) {
            VoidGlassFallingEntity.risingFall(level, pos, state);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(16) == 0) {
            BlockPos above = pos.above();
            if (isFree(level.getBlockState(above))) {
                level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state),
                        pos.getX() + random.nextDouble(),
                        pos.getY() + 1.0,
                        pos.getZ() + random.nextDouble(),
                        0.0, 0.0, 0.0);
            }
        }
    }
}
