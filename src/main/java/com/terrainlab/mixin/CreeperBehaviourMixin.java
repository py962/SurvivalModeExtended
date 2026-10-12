package com.terrainlab.mixin;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the creeper's player-targeting goal with a short-range one that ignores line of sight. */
@Mixin(Creeper.class)
public class CreeperBehaviourMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void modifyTargetGoal(CallbackInfo ci) {
        Creeper creeper = (Creeper)(Object)this;
        // Replace vanilla target goal (priority 1)
        creeper.targetSelector.getAvailableGoals().removeIf(
                goal -> goal.getGoal() instanceof NearestAttackableTargetGoal);
        creeper.targetSelector.addGoal(1,
                new NearestAttackableTargetGoal<>(creeper, Player.class, 10, false, false, null) {
                    @Override
                    public double getFollowDistance() {
                        return 8.0; // blocks
                    }
                });
    }
}
