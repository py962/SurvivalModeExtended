package com.terrainlab.mixin;

import com.terrainlab.difficulty.OverCapDifficulty;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Past the difficulty cap, skeletons shoot up to 50% faster (see OverCapDifficulty). */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonFasterShootingMixin {

    @Shadow @Final private RangedBowAttackGoal<AbstractSkeleton> bowGoal;

    @Inject(method = "reassessWeaponGoal", at = @At("TAIL"), require = 1)
    private void terrainlab$fasterShooting(CallbackInfo ci) {
        AbstractSkeleton self = (AbstractSkeleton) (Object) this;
        if (!(self.level() instanceof ServerLevel level)) return;
        float e = OverCapDifficulty.excess(level, self.blockPosition());
        if (e <= 0.0F) return;
        int interval = ((RangedBowAttackGoalAccessor) this.bowGoal).terrainlab$attackIntervalMin();
        this.bowGoal.setMinAttackInterval(Math.max(5, Math.round(interval / (1.0F + OverCapDifficulty.FASTER_SHOOTING * e))));
    }
}
