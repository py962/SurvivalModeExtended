package com.terrainlab.client.hud;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.ClientInternalTempState;
import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.season.VisualSeason;
import com.terrainlab.temperature.LocalTemperatureService;
import com.terrainlab.util.XpMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

public class TerrainLabDebugEntry implements DebugScreenEntry {

    @Override
    public void display(
            DebugScreenDisplayer displayer,
            @Nullable Level level,
            @Nullable LevelChunk clientChunk,
            @Nullable LevelChunk serverChunk) {

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        BlockPos pos = BlockPos.containing(player.getX(), player.getY(), player.getZ());

        long dayTime = mc.level.getOverworldClockTime();
        long seasonalOffset = ClientSeasonState.getSeasonalOffset();

        long gameTime = mc.level.getGameTime(); // seasons run on game time, like every other caller
        var season = SeasonManager.getCurrentTemperatureSeason(gameTime, seasonalOffset);
        VisualSeason visualSeason = SeasonManager.getCurrentVisualSeason(gameTime, seasonalOffset);

        long timeOfDayTicks = Math.floorMod(dayTime, 24000L);
        boolean isDay = com.terrainlab.season.SolarTime.isSolarDay(mc.level);

        double nightFraction = com.terrainlab.season.SolarTime.nightFraction(mc.level);
        double ambient = LocalTemperatureService.getAmbientTemperature(
                mc.level,
                pos,
                season,
                nightFraction
        );

        float bodyTemp = ClientInternalTempState.getInternalTemp();

        long dayNumber = Math.floorDiv(dayTime, 24000L);

        int hour = (int) ((timeOfDayTicks / 1000.0 + 6) % 24);
        int minute = (int) ((timeOfDayTicks % 1000) / 1000.0 * 60);

        displayer.addLine("TerrainLab Time: Day " + dayNumber + ", "
                + String.format("%02d:%02d", hour, minute)
                + (isDay ? " (Day)" : " (Night)")
                + String.format(" night %.0f%%", nightFraction * 100.0));

        displayer.addLine("TerrainLab Season: "
                + formatSeasonName(visualSeason.name())
                + ", "
                + formatSeasonName(season.name()));

        displayer.addLine("TerrainLab Local Temp: "
                + String.format("%.1f", ambient)
                + "C");

        displayer.addLine("TerrainLab Body Temp: "
                + String.format("%.1f", bodyTemp)
                + "C");

        // XP readout: "real" is level plus bar progress (spendable XP, see XpMath); vanilla
        // totalExperience never drops when levels are spent, so a gap means levels were spent.
        int needed = XpMath.xpNeededForLevel(player.experienceLevel);
        int intoLevel = Math.round(player.experienceProgress * needed);

        displayer.addLine("TerrainLab XP: Level "
                + player.experienceLevel
                + " ("
                + intoLevel
                + "/"
                + needed
                + "), real "
                + XpMath.realTotalXp(player)
                + " pts, vanilla total "
                + player.totalExperience
                + " pts");
    }

    private static String formatSeasonName(String enumName) {
        StringBuilder result = new StringBuilder();

        for (String part : enumName.split("_")) {
            result.append(part.charAt(0))
                    .append(part.substring(1).toLowerCase());
        }

        return result.toString();
    }
}
