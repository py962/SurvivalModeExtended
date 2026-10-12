package com.terrainlab.mixin;

import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Makes endermen place held blocks ten times as often (vanilla 1-in-2000 chance per tick). */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public class EndermanBehaviourMixin {

    @ModifyConstant(method = "canUse", constant = @Constant(intValue = 2000))
    private int increaseBlockPlacementRate(int original) {
        return 200;
    }
}
