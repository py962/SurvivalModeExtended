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
 * tinted by the client-computed ambient temperature and the fill by the server-synced
 * internal body temperature ({@link ClientInternalTempState}). Drawn at {@link #SCALE}
 * around a scaled anchor, with layout in unscaled local coordinates.
 */
public class TemperatureHudLayer implements GuiLayer {

    private static final Identifier FILL_TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/temperature_fill.png");
    private static final Identifier OUTLINE_TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/temperature_outline.png");

    private static final int FILL_W = 16, FILL_H = 32;
    // The outline art is the fill grown by 2px on every side.
    private static final int OUTLINE_W = 20, OUTLINE_H = 36;
    private static final int FILL_OFFSET = 2;

    private static final float SCALE = 0.7F;

    private static final int HOTBAR_HEIGHT = 22; // matches Gui.HOTBAR_SPRITE height
    private static final int BOTTOM_MARGIN = 4;
    // Additional lift above the hotbar, on top of BOTTOM_MARGIN.
    private static final int EXTRA_LIFT = 12;

    // Colour stops (degrees C, RGB), cold to hot: dark blue at 0 and below, blue at 5, green through
    // 15-20, yellow through 20-25, red at 35, dark red at 40 and above.
    private static final float[] STOP_TEMPS = {0.0F, 5.0F, 10.0F, 15.0F, 19.0F, 21.0F, 25.0F, 30.0F, 35.0F, 40.0F};
    private static final int[] STOP_COLOURS =
            {0x223F9E, 0x417DEB, 0x46C4C2, 0x54CC6F, 0x54CC6F, 0xE6CD40, 0xE6CD40, 0xEE8A28, 0xE13C2D, 0x9A1A16};

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

        int outlineTint = scaleColour((float) ambient);

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(SCALE, SCALE);

        graphics.blit(RenderPipelines.GUI_TEXTURED, OUTLINE_TEXTURE,
                0, 0, 0f, 0f, OUTLINE_W, OUTLINE_H, OUTLINE_W, OUTLINE_H, outlineTint);

        // Inner fill, tinted by internal body temperature.
        int fillTint = scaleColour(ClientInternalTempState.getInternalTemp());
        graphics.blit(RenderPipelines.GUI_TEXTURED, FILL_TEXTURE,
                FILL_OFFSET, FILL_OFFSET, 0f, 0f, FILL_W, FILL_H, FILL_W, FILL_H, fillTint);

        graphics.pose().popMatrix();
    }

    /** Colour of the gauge scale at this temperature, blended smoothly between stops. */
    static int scaleColour(float celsius) {
        if (celsius <= STOP_TEMPS[0]) {
            return 0xFF000000 | STOP_COLOURS[0];
        }
        for (int i = 1; i < STOP_TEMPS.length; i++) {
            if (celsius <= STOP_TEMPS[i]) {
                float t = (celsius - STOP_TEMPS[i - 1]) / (STOP_TEMPS[i] - STOP_TEMPS[i - 1]);
                return 0xFF000000 | lerpRgb(STOP_COLOURS[i - 1], STOP_COLOURS[i], t);
            }
        }
        return 0xFF000000 | STOP_COLOURS[STOP_COLOURS.length - 1];
    }

    private static int lerpRgb(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
