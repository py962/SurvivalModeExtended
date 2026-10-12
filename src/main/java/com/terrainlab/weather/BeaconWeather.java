package com.terrainlab.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Beacon weather control: confirming with a breeze rod in the payment slot
 * summons a thunderstorm, a blaze rod clears the sky. Overworld only, and only
 * when the weather would change. SeasonalWeatherCycleHandler stands aside while
 * the beacon's weather lasts (see {@link #isOverridden}).
 * <p>
 * Precipitation type still follows local temperature, so a winter storm snows;
 * vanilla only strikes lightning where it rains.
 */
public final class BeaconWeather {

    private BeaconWeather() {
    }

    // Durations in ticks.
    private static final int THUNDER_MIN = 6000, THUNDER_MAX = 12000; // 5-10 min
    private static final int CLEAR_MIN = 12000, CLEAR_MAX = 24000;    // 10-20 min

    public enum Kind { THUNDER, CLEAR }

    /** Game time until which a beacon controls each level's weather. Not saved. */
    private static final Map<net.minecraft.resources.ResourceKey<Level>, Long> overrideUntil = new ConcurrentHashMap<>();

    public static boolean isOverridden(Level level) {
        Long until = overrideUntil.get(level.dimension());
        return until != null && level.getGameTime() < until;
    }

    public static @Nullable Kind kindOf(ItemStack stack) {
        if (stack.is(Items.BREEZE_ROD)) {
            return Kind.THUNDER;
        }
        if (stack.is(Items.BLAZE_ROD)) {
            return Kind.CLEAR;
        }
        return null;
    }

    /** Why this rod can't be used right now, or null if it can. Works on client and server. */
    public static @Nullable Component problem(Level level, Kind kind, int beaconLevels) {
        if (beaconLevels <= 0) {
            return Component.translatable("beacon.terrainlab.weather.inactive");
        }
        if (level.dimension() != Level.OVERWORLD) {
            return Component.translatable("beacon.terrainlab.weather.no_sky");
        }
        if (kind == Kind.THUNDER && level.isThundering()) {
            return Component.translatable("beacon.terrainlab.weather.already_thundering");
        }
        if (kind == Kind.CLEAR && !level.isRaining() && !level.isThundering()) {
            return Component.translatable("beacon.terrainlab.weather.already_clear");
        }
        return null;
    }

    /** Tooltip for the confirm button when the rod can be used. */
    public static Component action(Kind kind) {
        return Component.translatable(kind == Kind.THUNDER
                ? "beacon.terrainlab.weather.summon_thunder"
                : "beacon.terrainlab.weather.clear_skies");
    }

    /** Server: change the weather, with sound and a particle burst up the beam. */
    public static void apply(ServerLevel level, BlockPos beaconPos, Kind kind) {
        RandomSource random = level.getRandom();
        if (kind == Kind.THUNDER) {
            int duration = THUNDER_MIN + random.nextInt(THUNDER_MAX - THUNDER_MIN + 1);
            level.setWeatherParameters(0, duration, true, true);
            overrideUntil.put(level.dimension(), level.getGameTime() + duration);
            level.playSound(null, beaconPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F, 0.9F);
        } else {
            int duration = CLEAR_MIN + random.nextInt(CLEAR_MAX - CLEAR_MIN + 1);
            level.setWeatherParameters(duration, 0, false, false);
            overrideUntil.put(level.dimension(), level.getGameTime() + duration);
            level.playSound(null, beaconPos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.5F, 0.8F);
            level.playSound(null, beaconPos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        var particle = kind == Kind.THUNDER ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.FLAME;
        double x = beaconPos.getX() + 0.5, z = beaconPos.getZ() + 0.5;
        for (int dy = 1; dy <= 32; dy += 2) {
            level.sendParticles(particle, x, beaconPos.getY() + dy, z, 6, 0.25, 0.6, 0.25, 0.02);
        }
    }
}
