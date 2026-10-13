package com.terrainlab.mixin;

import com.terrainlab.season.SolarTime;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



/**
 * Feeds the default environment timelines (sun and moon position, sky brightness, darkness,
 * monster burning) a seasonal {@link SolarTime} clock while the overworld clock itself stays untouched
 * for F3, /time, sleep, villager schedules and other tick-based systems.
 */
@Mixin(EnvironmentAttributeSystem.class)
public abstract class EnvironmentAttributeSystemSolarMixin {

    // Level passed to addDynamicLayers, read by the STORE modifier below (26.x: the timelines read a
    // ClockManager instead of a day-time LongSupplier, so the ClockManager local is wrapped).
    @Unique
    private static final ThreadLocal<Level> terrainlab$level = new ThreadLocal<>();

    @Inject(
            method = "addDynamicLayers",
            at = @At("HEAD"),
            require = 1
    )
    private static void terrainlab$captureLevel(
            EnvironmentAttributeSystem.Builder builder,
            Level level,
            CallbackInfo ci
    ) {
        terrainlab$level.set(level);
    }

    @ModifyVariable(
            method = "addDynamicLayers",
            at = @At("STORE"),
            ordinal = 0,
            require = 1
    )
    private static ClockManager terrainlab$solarClock(ClockManager raw) {
        Level level = terrainlab$level.get();

        if (level == null || !SolarTime.warps(level)) {
            return raw;
        }

        return new SolarTime.SolarClockManager(level, raw);
    }

    @Inject(
            method = "addDynamicLayers",
            at = @At("RETURN"),
            require = 1
    )
    private static void terrainlab$releaseLevel(
            EnvironmentAttributeSystem.Builder builder,
            Level level,
            CallbackInfo ci
    ) {
        terrainlab$level.remove();
    }
}
