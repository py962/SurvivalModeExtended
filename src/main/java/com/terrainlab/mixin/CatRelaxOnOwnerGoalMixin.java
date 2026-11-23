package com.terrainlab.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Cats don't follow their owner (see AnchoredPets), so the range at which a cat comes to lie on its
 * sleeping owner's bed (and later brings a morning gift) is raised from 10 to 32 blocks.
 */
@Mixin(targets = "net.minecraft.world.entity.animal.feline.Cat$CatRelaxOnOwnerGoal")
public abstract class CatRelaxOnOwnerGoalMixin {

    @Unique
    private static final double TERRAINLAB$RELAX_RANGE_SQR = 32.0 * 32.0;

    @ModifyConstant(method = "canUse", constant = @Constant(doubleValue = 100.0), require = 1)
    private double terrainlab$relaxRange(double original) {
        return TERRAINLAB$RELAX_RANGE_SQR;
    }
}
