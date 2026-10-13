package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrainlab.event.ModEndermanHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Makes endermen pick up blocks far more often (vanilla 1-in-20 chance per tick), but never a block
 * a player placed in the last day, or one an enderman placed continuing a player's pattern.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal")
public class EndermanTakeMixin {

    @ModifyConstant(method = "canUse", constant = @Constant(intValue = 20))
    private int increaseBlockPickupRate(int original) {
        return 3;
    }

    /** Treats a protected block as not holdable, so the goal leaves it alone. */
    @ModifyExpressionValue(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean skipProtectedBlocks(boolean holdable, @Local Level level, @Local BlockPos blockpos) {
        return holdable && !ModEndermanHandler.isProtected(level, blockpos);
    }
}
