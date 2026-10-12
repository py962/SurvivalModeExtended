package com.terrainlab.effect;

import com.terrainlab.energy.PlayerEnergyConstants;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.temperature.PlayerTemperatureConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Heatstroke: drains player energy by HEATSTROKE_ENERGY_DRAIN every HEATSTROKE_ENERGY_DRAIN_INTERVAL_TICKS
 * (1% per 5 s). The cadence follows game time, because the effect is reapplied with a short duration
 * every few ticks while body temperature stays at or above the threshold.
 */
public class HeatstrokeMobEffect extends MobEffect {
    public HeatstrokeMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
            return true;
        }
        if (level.getGameTime() % PlayerTemperatureConstants.HEATSTROKE_ENERGY_DRAIN_INTERVAL_TICKS != 0) {
            return true;
        }
        float energy = player.getData(ModAttachments.PLAYER_ENERGY.get());
        player.setData(ModAttachments.PLAYER_ENERGY.get(),
                Math.max(PlayerEnergyConstants.MIN_ENERGY, energy - PlayerTemperatureConstants.HEATSTROKE_ENERGY_DRAIN));
        return true;
    }
}
