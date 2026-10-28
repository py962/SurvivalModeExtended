package com.terrainlab.mixin;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RangedBowAttackGoal.class)
public interface RangedBowAttackGoalAccessor {
    @Accessor("attackIntervalMin")
    int terrainlab$attackIntervalMin();
}
