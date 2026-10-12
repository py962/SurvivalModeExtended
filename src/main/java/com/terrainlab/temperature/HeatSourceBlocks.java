package com.terrainlab.temperature;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Heat source strengths (degrees C added to ambient temperature at the source) and the test for indexable
 * heat-source blocks: lit campfires, magma and lava.
 */
public final class HeatSourceBlocks {

    // Applied undecayed up to 1 block away (see HeatSourceData#contributionAt).
    public static final double CAMPFIRE_STRENGTH = 40.0;
    public static final double LAVA_STRENGTH = 15.0;
    public static final double MAGMA_STRENGTH = 10.0;

    // Fire is not indexed (it changes constantly); HeatContributionService samples it live. Soul fire gives no heat.
    public static final double FIRE_STRENGTH = 20.0;

    private HeatSourceBlocks() {
    }

    public static double getStrength(BlockState state) {
        if (CampfireBlock.isLitCampfire(state)) {
            return CAMPFIRE_STRENGTH;
        }
        if (state.is(Blocks.MAGMA_BLOCK)) {
            return MAGMA_STRENGTH;
        }
        var fluidType = state.getFluidState().getType();
        if (fluidType == Fluids.LAVA || fluidType == Fluids.FLOWING_LAVA) {
            return LAVA_STRENGTH;
        }
        return 0.0;
    }

    public static boolean isHeatSource(BlockState state) {
        return getStrength(state) > 0.0;
    }
}
