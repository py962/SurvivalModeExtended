package com.terrainlab.temperature;

import com.terrainlab.season.SeasonalBiomeData;
import com.terrainlab.season.VisualSeason;
import com.terrainlab.weather.WeatherConstants;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.terrainlab.season.SeasonalBiomeData.PrecipitationBehaviour.*;

public final class BiomeTemperatureTable {

    private static final Map<Identifier, SeasonalBiomeData> TABLE = new HashMap<>();
    private static boolean bootstrapped = false;

    /** Biomes with no entry by design; they take their neighbours' temperature during blending. */
    public static final Set<Identifier> INHERITS_SURROUNDINGS = new HashSet<>();

    private BiomeTemperatureTable() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) return;

        // Order: midwinterDay, midwinterNight, nearWinterDay, nearWinterNight,
        // nearSummerDay, nearSummerNight, midsummerDay, midsummerNight, precipitation.

        put("minecraft:eroded_badlands",
                new SeasonalBiomeData(41.3, 18.3, 47.5, 24.5, 54.8, 32.8, 60.0, 38.0, NONE));
        put("minecraft:desert",
                new SeasonalBiomeData(40.3, 16.3, 45.7, 22.7, 52.0, 30.0, 56.3, 36.3, NONE));
        put("minecraft:badlands",
                new SeasonalBiomeData(36.3, 16.3, 42.5, 22.5, 49.8, 29.8, 55.0, 35.0, NONE));

        SeasonalBiomeData savanna =
                new SeasonalBiomeData(31.8, 20.8, 34.5, 25.5, 44.3, 31.3, 50.0, 35.0, SEASONAL_WET_DRY);
        put("minecraft:savanna", savanna);
        put("minecraft:savanna_plateau", savanna);
        put("minecraft:windswept_savanna", savanna);

        // Preserve wooded badlands temperatures, but use ordinary local precipitation settings.
        put("minecraft:wooded_badlands",
                new SeasonalBiomeData(31.8, 20.8, 34.5, 25.5, 44.3, 31.3, 50.0, 35.0, YEAR_ROUND_RAIN));

        SeasonalBiomeData jungle =
                new SeasonalBiomeData(30.0, 28.0, 34.0, 32.0, 36.0, 34.0, 40.0, 38.0, YEAR_ROUND_RAIN);
        put("minecraft:jungle", jungle);
        put("minecraft:bamboo_jungle", jungle);
        put("minecraft:mangrove_swamp", jungle);
        put("minecraft:sparse_jungle", jungle);

        SeasonalBiomeData cherryGrove =
                new SeasonalBiomeData(18.0, 13.0, 23.0, 16.8, 28.0, 20.5, 33.0, 24.3, YEAR_ROUND_RAIN);
        put("minecraft:cherry_grove", cherryGrove);
        put("minecraft:dark_forest", cherryGrove);
        put("minecraft:pale_garden", cherryGrove);
        put("minecraft:flower_forest", cherryGrove);
        put("minecraft:sunflower_plains", cherryGrove);

        put("minecraft:swamp",
                new SeasonalBiomeData(13.0, 11.5, 18.0, 15.5, 26.0, 22.5, 34.0, 30.5, YEAR_ROUND_RAIN));

        put("minecraft:forest",
                new SeasonalBiomeData(9.3, 3.0, 18.5, 9.3, 25.8, 15.5, 31.0, 24.8, YEAR_ROUND_RAIN));

        put("minecraft:plains",
                new SeasonalBiomeData(5.5, -4.5, 16.0, 3.0, 25.5, 14.5, 34.0, 18.0, FOUR_SEASONS_NO_SNOW_SETTLING));

        SeasonalBiomeData birchForest =
                new SeasonalBiomeData(4.3, -2.0, 10.5, 4.3, 16.8, 10.5, 23.0, 16.8, FOUR_SEASONS_NO_SNOW_SETTLING);
        put("minecraft:birch_forest", birchForest);
        put("minecraft:old_growth_birch_forest", birchForest);
        put("minecraft:windswept_forest", birchForest);

        SeasonalBiomeData windsweptHills =
                new SeasonalBiomeData(2.5, -5.5, 9.0, 1.0, 15.5, 7.5, 22.0, 14.0, FOUR_SEASONS_NO_SNOW_SETTLING);
        put("minecraft:windswept_hills", windsweptHills);
        put("minecraft:windswept_gravelly_hills", windsweptHills);

        SeasonalBiomeData marsh =
                new SeasonalBiomeData(-1.8, -6.0, 4.5, -1.8, 10.8, 4.5, 17.0, 10.8, FOUR_SEASONS_SNOW_IN_WINTER);
        put("terrainlab:marsh", marsh);
        put("minecraft:meadow", marsh);

        SeasonalBiomeData taiga =
                new SeasonalBiomeData(-1.8, -10.0, 4.5, -3.8, 10.8, 2.5, 15.0, 8.8, FOUR_SEASONS_SNOW_IN_WINTER);
        put("minecraft:old_growth_pine_taiga", taiga);
        put("minecraft:old_growth_spruce_taiga", taiga);
        put("minecraft:taiga", taiga);

        SeasonalBiomeData frozenMarsh =
                new SeasonalBiomeData(-13.0, -20.0, -10.0, -16.3, -7.0, -12.5, -3.0, -8.8, SNOW_ALL_YEAR_NO_THAW);
        put("terrainlab:frozen_marsh", frozenMarsh);
        put("minecraft:grove", frozenMarsh);
        put("minecraft:snowy_taiga", frozenMarsh);

        SeasonalBiomeData snowyPlains =
                new SeasonalBiomeData(-13.0, -25.0, -10.0, -20.0, -7.0, -15.0, -3.0, -10.0, SNOW_ALL_YEAR_NO_THAW);
        put("minecraft:snowy_plains", snowyPlains);
        put("minecraft:snowy_slopes", snowyPlains);

        put("minecraft:ice_spikes",
                new SeasonalBiomeData(-22.0, -30.0, -18.0, -25.0, -13.0, -20.0, -8.0, -15.0, SNOW_ALL_YEAR_NO_THAW));

        // Oceans.
        put("minecraft:warm_ocean",
                new SeasonalBiomeData(24, 22, 27, 25, 30, 28, 32, 30, YEAR_ROUND_RAIN));
        put("minecraft:lukewarm_ocean",
                new SeasonalBiomeData(16, 14, 19, 17, 22, 20, 24, 22, YEAR_ROUND_RAIN));
        put("minecraft:deep_lukewarm_ocean",
                new SeasonalBiomeData(15, 13, 18, 16, 21, 19, 23, 21, YEAR_ROUND_RAIN));
        put("minecraft:ocean",
                new SeasonalBiomeData(8, 6, 11, 9, 14, 12, 16, 14, YEAR_ROUND_RAIN));
        put("minecraft:deep_ocean",
                new SeasonalBiomeData(7, 5, 10, 8, 13, 11, 15, 13, YEAR_ROUND_RAIN));
        put("minecraft:cold_ocean",
                new SeasonalBiomeData(0, -2, 3, 1, 6, 4, 8, 6, YEAR_ROUND_RAIN));
        put("minecraft:deep_cold_ocean",
                new SeasonalBiomeData(-1, -3, 2, 0, 5, 3, 7, 5, YEAR_ROUND_RAIN));
        put("minecraft:frozen_ocean",
                new SeasonalBiomeData(-10, -12, -7, -9, -4, -6, -2, -4, YEAR_ROUND_RAIN));
        put("minecraft:deep_frozen_ocean",
                new SeasonalBiomeData(-13, -15, -10, -13, -7, -11, -5, -8, YEAR_ROUND_RAIN));

        // Caves, Nether and End: non-seasonal.
        put("terrainlab:ice_caves", nonSeasonal(-25));
        put("minecraft:dripstone_caves", nonSeasonal(0));
        put("minecraft:lush_caves", nonSeasonal(20));
        put("minecraft:deep_dark", nonSeasonal(0));

        put("minecraft:nether_wastes", nonSeasonal(50));
        put("minecraft:crimson_forest", nonSeasonal(45));
        put("minecraft:warped_forest", nonSeasonal(45));
        put("minecraft:soul_sand_valley", nonSeasonal(45));
        put("minecraft:basalt_deltas", nonSeasonal(55));

        put("minecraft:the_end", nonSeasonal(-10));
        put("minecraft:end_barrens", nonSeasonal(-12));
        put("minecraft:end_highlands", nonSeasonal(-8));
        put("minecraft:end_midlands", nonSeasonal(-10));
        put("minecraft:small_end_islands", nonSeasonal(-5));

        inheritsSurroundings("minecraft:river");
        inheritsSurroundings("minecraft:frozen_river");
        inheritsSurroundings("minecraft:beach");
        inheritsSurroundings("minecraft:snowy_beach");
        inheritsSurroundings("minecraft:stony_shore");
        inheritsSurroundings("minecraft:frozen_peaks");
        inheritsSurroundings("minecraft:jagged_peaks");
        inheritsSurroundings("minecraft:stony_peaks");
        inheritsSurroundings("minecraft:mushroom_fields");
        inheritsSurroundings("terrainlab:inland_river");

        bootstrapped = true;
    }

    /**
     * Local eligibility percentage for a biome and visual season.
     * Wooded badlands intentionally fall through to the ordinary biome settings.
     */
    public static double getLocalPrecipitationPercent(Identifier biomeId, VisualSeason season) {
        boolean wetSeason = season == VisualSeason.AUTUMN || season == VisualSeason.WINTER;
        String id = biomeId.toString();

        if (id.equals("minecraft:savanna")
                || id.equals("minecraft:savanna_plateau")
                || id.equals("minecraft:windswept_savanna")) {
            return wetSeason
                    ? WeatherConstants.LOCAL_SAVANNA_WET_SEASON_PERCENT
                    : WeatherConstants.LOCAL_SAVANNA_DRY_SEASON_PERCENT;
        }

        if (id.equals("minecraft:jungle")
                || id.equals("minecraft:bamboo_jungle")
                || id.equals("minecraft:sparse_jungle")
                || id.equals("minecraft:mangrove_swamp")) {
            return wetSeason
                    ? WeatherConstants.LOCAL_JUNGLE_WET_SEASON_PERCENT
                    : WeatherConstants.LOCAL_JUNGLE_DRY_SEASON_PERCENT;
        }

        return wetSeason
                ? WeatherConstants.LOCAL_DEFAULT_WET_SEASON_PERCENT
                : WeatherConstants.LOCAL_DEFAULT_DRY_SEASON_PERCENT;
    }

    private static SeasonalBiomeData nonSeasonal(double celsius) {
        return new SeasonalBiomeData(celsius, celsius, celsius, celsius, celsius, celsius, celsius, celsius, NONE);
    }

    private static void put(String identifier, SeasonalBiomeData data) {
        TABLE.put(Identifier.parse(identifier), data);
    }

    private static void inheritsSurroundings(String identifier) {
        INHERITS_SURROUNDINGS.add(Identifier.parse(identifier));
    }

    public static SeasonalBiomeData get(Identifier biomeId) {
        if (!bootstrapped) bootstrap();
        return TABLE.get(biomeId);
    }

    public static boolean has(Identifier biomeId) {
        if (!bootstrapped) bootstrap();
        return TABLE.containsKey(biomeId);
    }
}