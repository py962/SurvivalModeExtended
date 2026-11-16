package com.terrainlab.mixin;

import com.terrainlab.event.MobEffectAiHandler;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blind mobs run no movement or look goals and no target goals; nauseous ones no target goals. */
@Mixin(Mob.class)
public abstract class MobEffectAiMixin {

    @Inject(method = "updateControlFlags", at = @At("TAIL"), require = 1)
    private void terrainlab$effectControlFlags(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        boolean blind = MobEffectAiHandler.isBlind(self);
        if (blind) {
            self.goalSelector.setControlFlag(Goal.Flag.MOVE, false);
            self.goalSelector.setControlFlag(Goal.Flag.LOOK, false);
        }
        self.targetSelector.setControlFlag(Goal.Flag.TARGET, !blind && !MobEffectAiHandler.isFrenzied(self));
    }
}
