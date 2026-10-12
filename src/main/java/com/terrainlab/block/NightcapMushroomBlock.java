package com.terrainlab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * White nightcap mushroom; survives only on mossy cobblestone. Biome restrictions belong to
 * worldgen placement, not to {@code canSurvive}.
 *
 * <p>TODO: add the configured/placed feature for Old Growth Taiga biomes, and optional
 * bonemeal spreading. Not yet generated in the world.
 */
public class NightcapMushroomBlock extends BushBlock {

    public NightcapMushroomBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(Blocks.MOSSY_COBBLESTONE);
    }
}
