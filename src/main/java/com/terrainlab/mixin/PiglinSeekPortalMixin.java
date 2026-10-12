package com.terrainlab.mixin;

import com.terrainlab.entity.ai.PiglinSeekPortalGoal;
import net.minecraft.world.entity.monster.piglin.Piglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds {@link PiglinSeekPortalGoal} to Piglin (not other AbstractPiglins) at the end of its constructor;
 * Piglin does not declare registerGoals() itself, so there is no local method to inject into.
 */
@Mixin(Piglin.class)
public abstract class PiglinSeekPortalMixin extends net.minecraft.world.entity.monster.piglin.AbstractPiglin {

    public PiglinSeekPortalMixin(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.monster.piglin.AbstractPiglin> type, net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void terrainlab$addSeekPortalGoal(CallbackInfo ci) {
        this.goalSelector.addGoal(6, new PiglinSeekPortalGoal((Piglin) (Object) this));
    }
}
