package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.season.VisualSeason;
import com.terrainlab.weather.BeaconWeather;
import com.terrainlab.weather.WeatherConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.ThreadLocalRandom;

@EventBusSubscriber(modid = TerrainLab.MODID)
public final class SeasonalWeatherCycleHandler {

    // Game time until which a /weather command's result is left alone.
    private static long manualWeatherUntil = Long.MIN_VALUE;
    private static boolean manualWeatherPending;

    private SeasonalWeatherCycleHandler() {
    }

    /** A /weather command takes over for the duration it set. */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        String input = event.getParseResults().getReader().getString().trim();

        if (input.startsWith("/")) {
            input = input.substring(1);
        }

        if (input.equals("weather") || input.startsWith("weather ")) {
            manualWeatherPending = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount()
                % WeatherConstants.CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() != Level.OVERWORLD) {
                continue;
            }

            tick(level);
        }
    }

    private static void tick(ServerLevel level) {
        // A beacon weather override is active; leave it alone until it expires.
        if (BeaconWeather.isOverridden(level)) {
            return;
        }

        if (!level.getGameRules().get(GameRules.ADVANCE_WEATHER)) {
            return;
        }

        if (manualWeatherPending) {
            manualWeatherPending = false;

            WeatherData data = level.getWeatherData();
            int remaining = level.isRaining()
                    ? data.getRainTime()
                    : data.getClearWeatherTime();

            manualWeatherUntil = level.getGameTime() + Math.max(remaining, 0);
        }

        if (level.getGameTime() < manualWeatherUntil) {
            return;
        }

        SeasonSavedData seasonData = SeasonSavedData.get(level);
        seasonData.generateOffsetIfAbsent(level.getSeed());

        Season season = SeasonManager.getCurrentSeason(
                level.getGameTime(),
                seasonData.getSeasonalOffset()
        );

        VisualSeason visualSeason = season.visualSeason();
        boolean wetSeason = isWetSeason(visualSeason);
        boolean autumn = visualSeason == VisualSeason.AUTUMN;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean raining = level.isRaining();

        if (wetSeason) {
            if (raining) {
                return; // Wet-season rain is allowed to finish naturally.
            }

            if (WeatherConstants.rollPercent(
                    random,
                    WeatherConstants.GLOBAL_WET_SEASON_RAIN_START_PERCENT
            )) {
                startRain(
                        level,
                        random,
                        WeatherConstants.WET_SEASON_RAIN_DURATION_MIN_TICKS,
                        WeatherConstants.WET_SEASON_RAIN_DURATION_MAX_TICKS,
                        autumn
                );
            } else {
                forceClear(
                        level,
                        random,
                        WeatherConstants.WET_SEASON_CLEAR_DURATION_MIN_TICKS,
                        WeatherConstants.WET_SEASON_CLEAR_DURATION_MAX_TICKS
                );
            }

            return;
        }

        if (raining) {
            if (WeatherConstants.rollPercent(
                    random,
                    WeatherConstants.GLOBAL_DRY_SEASON_RAIN_END_PERCENT
            )) {
                forceClear(
                        level,
                        random,
                        WeatherConstants.DRY_SEASON_CLEAR_DURATION_MIN_TICKS,
                        WeatherConstants.DRY_SEASON_CLEAR_DURATION_MAX_TICKS
                );
            }

            return;
        }

        if (WeatherConstants.rollPercent(
                random,
                WeatherConstants.GLOBAL_DRY_SEASON_SHOWER_START_PERCENT
        )) {
            startRain(
                    level,
                    random,
                    WeatherConstants.DRY_SEASON_SHOWER_DURATION_MIN_TICKS,
                    WeatherConstants.DRY_SEASON_SHOWER_DURATION_MAX_TICKS,
                    false
            );
        } else {
            forceClear(
                    level,
                    random,
                    WeatherConstants.DRY_SEASON_CLEAR_DURATION_MIN_TICKS,
                    WeatherConstants.DRY_SEASON_CLEAR_DURATION_MAX_TICKS
            );
        }
    }

    private static boolean isWetSeason(VisualSeason visualSeason) {
        return visualSeason == VisualSeason.WINTER
                || visualSeason == VisualSeason.AUTUMN;
    }

    private static void startRain(
            ServerLevel level,
            ThreadLocalRandom random,
            int minTicks,
            int maxTicks,
            boolean allowThunder
    ) {
        int duration = minTicks + random.nextInt(maxTicks - minTicks + 1);

        boolean thunder = allowThunder
                && WeatherConstants.rollPercent(
                random,
                WeatherConstants.THUNDERSTORM_PERCENT
        );

        level.getServer().setWeatherParameters(0, duration, true, thunder);
    }

    private static void forceClear(
            ServerLevel level,
            ThreadLocalRandom random,
            int minTicks,
            int maxTicks
    ) {
        int duration = minTicks + random.nextInt(maxTicks - minTicks + 1);
        level.getServer().setWeatherParameters(duration, 0, false, false);
    }
}