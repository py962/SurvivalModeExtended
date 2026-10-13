package com.terrainlab.mixin;

import com.terrainlab.season.SolarTime;
import net.minecraft.core.Holder;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.timeline.Timelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;



/**
 * Seasonal day length: keeps the raw clock for timelines that must not follow solar time.
 * <ul>
 *   <li>villager_schedule - villager work/sleep hours stay fixed.</li>
 *   <li>early_game - counts total ticks (patrols after day 5).</li>
 * </ul>
 * The day and moon timelines keep the solar clock, so the moon phase flips at solar dawn.
 */
@Mixin(EnvironmentAttributeSystem.Builder.class)
public abstract class EnvironmentAttributeSystemBuilderSolarMixin {

    @ModifyVariable(method = "addTimelineLayer", at = @At("HEAD"), argsOnly = true, require = 1)
    private ClockManager terrainlab$rawClockForOptOuts(ClockManager clock, Holder<Timeline> timeline) {
        if (clock instanceof SolarTime.SolarClockManager solar
                && (timeline.is(Timelines.VILLAGER_SCHEDULE) || timeline.is(Timelines.EARLY_GAME))) {
            return solar.raw();
        }
        return clock;
    }
}
