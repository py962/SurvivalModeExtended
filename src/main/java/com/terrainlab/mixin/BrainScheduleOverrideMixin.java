package com.terrainlab.mixin;

import com.terrainlab.hearth.BrainScheduleOverride;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** Applies the override with vanilla's throttle and requirement check, so forced activities such as panic still win. */
@Mixin(Brain.class)
public abstract class BrainScheduleOverrideMixin implements BrainScheduleOverride {

    @Shadow
    private long lastScheduleUpdate;

    @Shadow
    @Final
    private Set<Activity> activeActivities;

    @Shadow
    public abstract void setActiveActivityIfPossible(Activity activity);

    @Unique
    private @Nullable Activity terrainlab$scheduleOverride;

    @Override
    public void terrainlab$setScheduleOverride(@Nullable Activity activity) {
        this.terrainlab$scheduleOverride = activity;
    }

    @Inject(method = "updateActivityFromSchedule", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$applyOverride(EnvironmentAttributeSystem environmentAttributes, long gameTime, Vec3 pos, CallbackInfo ci) {
        Activity override = this.terrainlab$scheduleOverride;
        if (override == null) {
            return;
        }
        ci.cancel();
        if (gameTime - this.lastScheduleUpdate > 20L) {
            this.lastScheduleUpdate = gameTime;
            if (!this.activeActivities.contains(override)) {
                this.setActiveActivityIfPossible(override);
            }
        }
    }
}
