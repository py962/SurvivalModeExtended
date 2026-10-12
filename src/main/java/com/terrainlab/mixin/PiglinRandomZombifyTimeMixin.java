package com.terrainlab.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Replaces the piglin overworld-zombification time (vanilla 300 ticks) with a per-instance value
 * drawn uniformly from 900-1500 ticks, so piglins don't convert in lockstep. Applies to piglins and
 * brutes. The threshold is sampled lazily on first use; -1 marks it unset.
 */
@Mixin(AbstractPiglin.class)
public abstract class PiglinRandomZombifyTimeMixin {

    private static final int AVERAGE_TICKS = 1200; // 60 s
    private static final int RANGE_TICKS = 600;    // total spread, +/- 300 ticks

    @Unique
    private int terrainlab$conversionThreshold = -1;

    @ModifyConstant(method = "customServerAiStep", constant = @Constant(intValue = 300), require = 1)
    private int terrainlab$randomizeConversionThreshold(int original) {
        AbstractPiglin self = (AbstractPiglin) (Object) this;
        if (terrainlab$conversionThreshold < 0) {
            int min = AVERAGE_TICKS - RANGE_TICKS / 2;
            int max = AVERAGE_TICKS + RANGE_TICKS / 2;
            terrainlab$conversionThreshold = Mth.nextInt(self.getRandom(), min, max);
        }
        return terrainlab$conversionThreshold;
    }
}
