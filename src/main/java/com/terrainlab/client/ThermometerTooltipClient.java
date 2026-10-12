package com.terrainlab.client;

import com.terrainlab.season.SeasonManager;
import com.terrainlab.season.TemperatureSeason;
import com.terrainlab.temperature.LocalTemperatureService;
import com.terrainlab.temperature.ThermometerBand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Client-side thermometer reading for the item tooltip, taken at the local player's position with the
 * same season and day inputs as the thermometer item model. Only called on the logical client.
 */
public final class ThermometerTooltipClient {

    private ThermometerTooltipClient() {
    }

    @Nullable
    public static ThermometerBand currentBand() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }

        Level level = player.level();
        BlockPos pos = player.blockPosition();
        long offset = ClientSeasonState.getSeasonalOffset();
        TemperatureSeason season = SeasonManager.getCurrentTemperatureSeason(level.getGameTime(), offset);
        double nightFraction = com.terrainlab.season.SolarTime.nightFraction(level);
        double celsius = LocalTemperatureService.getAmbientTemperature(level, pos, season, nightFraction);
        return ThermometerBand.fromCelsius(celsius);
    }
}
