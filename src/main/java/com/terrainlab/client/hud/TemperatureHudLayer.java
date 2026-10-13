package com.terrainlab.client.hud;

import com.terrainlab.client.ClientInternalTempState;
import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.temperature.LocalTemperatureService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.gui.GuiLayer;

/**
 * Temperature HUD gauge drawn bottom-center above the hotbar. The silhouette outline is
 * tinted by the client-computed ambient temperature; the arrow marks the server-synced
 * internal body temperature ({@link ClientInternalTempState}). Drawn at {@link #SCALE}
 * around a scaled anchor, with layout in unscaled local coordinates.
 */
public class TemperatureHudLayer implements GuiLayer {

    private static final Identifier FILL_TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/temperature_fill.png");
    private static final Identifier OUTLINE_TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/temperature_outline.png");
    private static final Identifier ARROW_TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/temperature_arrow.png");

    private static final int FILL_W = 16, FILL_H = 32;
    private static final int OUTLINE_W = 18, OUTLINE_H = 34;
    private static final int ARROW_W = 14, ARROW_H = 12;

    private static final float SCALE = 0.7F;

    // Must match the mapping baked into the gauge art (degrees C).
    private static final float DISPLAY_MIN = -20.0F;
    private static final float DISPLAY_MAX = 60.0F;
    private static final float DAMAGE_LOW = 0.0F;
    private static final float DAMAGE_HIGH = 40.0F;

    private static final int HOTBAR_HEIGHT = 22; // matches Gui.HOTBAR_SPRITE height
    private static final int BOTTOM_MARGIN = 4;
    // Additional lift above the hotbar, on top of BOTTOM_MARGIN.
    private static final int EXTRA_LIFT = 12;

    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_RED = 0xFFE1503C;
    private static final int COLOR_BLUE = 0xFF417DEB;

    @Override
    public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (player.isSpectator() || player.getAbilities().instabuild) {
            return;
        }
        // Anchor uses the scaled outline size so the gauge stays centered above the hotbar.
        int scaledOutlineW = Math.round(OUTLINE_W * SCALE);
        int scaledOutlineH = Math.round(OUTLINE_H * SCALE);
        int x = graphics.guiWidth() / 2 - scaledOutlineW / 2;
        int y = graphics.guiHeight() - HOTBAR_HEIGHT - scaledOutlineH - BOTTOM_MARGIN - EXTRA_LIFT;

        // Outline: tinted by ambient (external) temperature.
        BlockPos pos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        long gameTime = mc.level.getGameTime();
        long seasonalOffset = ClientSeasonState.getSeasonalOffset();
        var season = SeasonManager.getCurrentTemperatureSeason(gameTime, seasonalOffset);
        double nightFraction = com.terrainlab.season.SolarTime.nightFraction(mc.level); // matches the server
        double ambient = LocalTemperatureService.getAmbientTemperature(mc.level, pos, season, nightFraction);

        int outlineTint;
        if (ambient < DAMAGE_LOW) {
            outlineTint = COLOR_BLUE;
        } else if (ambient > DAMAGE_HIGH) {
            outlineTint = COLOR_RED;
        } else {
            outlineTint = COLOR_WHITE;
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(SCALE, SCALE);

        graphics.blit(RenderPipelines.GUI_TEXTURED, OUTLINE_TEXTURE,
                0, 0, 0f, 0f, OUTLINE_W, OUTLINE_H, OUTLINE_W, OUTLINE_H, outlineTint);

        // Inner fill: centered with a 1px margin inside the 18x34 outline.
        int fillX = 1;
        int fillY = 1;
        graphics.blit(RenderPipelines.GUI_TEXTURED, FILL_TEXTURE,
                fillX, fillY, 0f, 0f, FILL_W, FILL_H, FILL_W, FILL_H, COLOR_WHITE);

        // Arrow: internal temperature on the same DISPLAY_MIN..DISPLAY_MAX scale.
        float internalTemp = ClientInternalTempState.getInternalTemp();
        float clamped = Math.max(DISPLAY_MIN, Math.min(DISPLAY_MAX, internalTemp));
        float frac = (DISPLAY_MAX - clamped) / (DISPLAY_MAX - DISPLAY_MIN); // 0 = top (hot), 1 = bottom (cold)
        int arrowY = fillY + Math.round(frac * (FILL_H - 1)) - ARROW_H / 2;
        int arrowX = fillX - ARROW_W + 2; // tip overlaps the silhouette's left edge

        graphics.blit(RenderPipelines.GUI_TEXTURED, ARROW_TEXTURE,
                arrowX, arrowY, 0f, 0f, ARROW_W, ARROW_H, ARROW_W, ARROW_H, COLOR_WHITE);

        graphics.pose().popMatrix();
    }
}
