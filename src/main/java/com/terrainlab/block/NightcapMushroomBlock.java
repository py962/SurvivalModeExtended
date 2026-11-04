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
 * <p>Generated in the old growth taigas by the nightcap_mushroom_patch feature.
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
