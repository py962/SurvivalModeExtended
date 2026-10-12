package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModMobEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.minecraft.util.Mth;

/**
 * Heatstroke HUD vignette: a red screen-edge tint with a smoothstep elliptical falloff,
 * pulsed by a double-beat heartbeat. Drawn as a coarse grid of solid fills because
 * GuiGraphics has no per-pixel shading.
 * <p>
 * TODO: scale the peak alpha with how far the internal temperature exceeds the
 * heatstroke threshold.
 */
public class HeatstrokeVignetteLayer implements GuiLayer {

    private static final float MAX_ALPHA = 0.55F; // peak opacity
    private static final int TINT_COLOR = 0xCC2222;

    // Falloff bounds as normalized elliptical distance from center (1 = edge midpoint).
    private static final float INNER_RADIUS = 0.45F;
    private static final float OUTER_RADIUS = 1.05F;

    // Grid resolution of the radial approximation; higher is smoother but costs more fills.
    private static final int GRID_COLS = 40;
    private static final int GRID_ROWS = 24;

    // Heartbeat cycles (lub-dub pairs) per second.
    private static final float HEARTBEAT_SPEED = 0.75F;
    private static final float BASELINE_FRACTION = 0.55F;  // alpha floor between beats, fraction of MAX_ALPHA
    private static final float RADIUS_THROB_AMOUNT = 0.05F; // OUTER_RADIUS swell per beat

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!player.hasEffect(ModMobEffects.HEATSTROKE)) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float centerX = width / 2F;
        float centerY = height / 2F;

        float maxAlpha = MAX_ALPHA;

        float gameTime = (mc.level != null)
                ? (mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false)) / 20F
                : 0F;

        float beat = heartbeatPulse(gameTime * HEARTBEAT_SPEED); // 0..1
        float peakAlpha = maxAlpha * (BASELINE_FRACTION + (1F - BASELINE_FRACTION) * beat);
        float outerRadius = OUTER_RADIUS * (1F + RADIUS_THROB_AMOUNT * beat);

        float cellW = (float) width / GRID_COLS;
        float cellH = (float) height / GRID_ROWS;

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                float cellCenterX = (col + 0.5F) * cellW;
                float cellCenterY = (row + 0.5F) * cellH;

                float dx = (cellCenterX - centerX) / centerX;
                float dy = (cellCenterY - centerY) / centerY;
                float dist = Mth.sqrt(dx * dx + dy * dy);

                float t = Mth.clamp((dist - INNER_RADIUS) / (outerRadius - INNER_RADIUS), 0F, 1F);
                float eased = smoothstep(t);
                float alpha = eased * peakAlpha;

                if (alpha <= 0.002F) continue;

                int x1 = Math.round(col * cellW);
                int y1 = Math.round(row * cellH);
                int x2 = Math.round((col + 1) * cellW);
                int y2 = Math.round((row + 1) * cellH);

                graphics.fill(x1, y1, x2, y2, withAlpha(TINT_COLOR, alpha));
            }
        }
    }

    private static float smoothstep(float t) {
        return t * t * (3F - 2F * t);
    }

    // Two sharp-attack, decaying pulses per cycle ("lub-dub"). Returns 0..1.
    private static float heartbeatPulse(float t) {
        float cycle = t - Mth.floor(t); // 0..1
        float lub = pulseShape((cycle + 1F) % 1F);
        float dub = pulseShape((cycle - 0.18F + 1F) % 1F);
        return Math.max(lub, dub * 0.7F);
    }

    // Pulse occupying the first 15% of the 0..1 cycle position; zero elsewhere.
    private static float pulseShape(float x) {
        if (x < 0F || x > 0.15F) return 0F;
        float local = x / 0.15F;
        return (float) Math.exp(-local * 4F) * (1F - local);
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = (int) (alpha * 255) & 0xFF;
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
