package com.terrainlab.client.hud;

import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.temperature.LocalTemperatureService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/** F3 debug entry showing the current temperature season and local ambient temperature. */

public class DebugEntrySeasonTemperature implements DebugScreenEntry {

    public static final Identifier GROUP = Identifier.fromNamespaceAndPath("terrainlab", "season_temperature");

    public DebugEntrySeasonTemperature() {
    }

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level level, @Nullable LevelChunk clientChunk, @Nullable LevelChunk serverChunk) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.getCameraEntity();
        if (entity == null || minecraft.level == null) {
            return;
        }

        BlockPos pos = entity.blockPosition();
        if (!minecraft.level.isInsideBuildHeight(pos.getY())) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        long seasonalOffset = ClientSeasonState.getSeasonalOffset();
        var season = SeasonManager.getCurrentTemperatureSeason(gameTime, seasonalOffset);
        double nightFraction = com.terrainlab.season.SolarTime.nightFraction(minecraft.level);
        double ambient = LocalTemperatureService.getAmbientTemperature(minecraft.level, pos, season, nightFraction);

        displayer.addLine("Season: " + season.name());
        displayer.addLine("Local Temp: " + String.format("%.1f", ambient) + "C");

        // Temporary diagnostic: client vs (single-player) server solar time and sky darkness,
        // which should always match.
        long raw = Math.floorMod(minecraft.level.getDayTime(), 24000L);
        String line = "Solar time: client " + com.terrainlab.season.SolarTime.solarTimeOfDay(minecraft.level)
                + " (darken " + minecraft.level.getSkyDarken() + ")";
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            line += " / server " + com.terrainlab.season.SolarTime.solarTimeOfDay(serverLevel)
                    + " (darken " + serverLevel.getSkyDarken() + ")";
        }
        displayer.addLine(line + " / F3 " + raw);
    }
}
