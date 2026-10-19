package com.terrainlab.client.hud;

import com.terrainlab.client.ClientEnergyState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.gui.GuiLayer;

import static com.terrainlab.energy.PlayerEnergyConstants.*;

/**
 * Energy HUD bar, drawn in place of the vanilla armor bar. Twelve segments of the
 * energy_bar.png strip span 0..MAX_ENERGY; filled segments draw at full opacity and the
 * rest dimmed, and segments above REST_CAP get a gold tint scaled by boost amount. Filled
 * segments pulse gold on a discrete energy gain (ClientStatFlash).
 * Hidden in Creative and Spectator.
 */
public class EnergyBarLayer implements GuiLayer {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/energy_bar.png");
    // White mask of the same strip, tinted gold for the energy-gain flash (see ClientStatFlash).
    private static final Identifier FLASH_TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/energy_bar_flash.png");

    private static final int SEGMENT_COUNT = 12;
    private static final int FRAME_W = 16;
    private static final int FRAME_H = 17;

    private static final float SCALE = 0.48F;

    // Extra offset from the armor-bar anchor, in screen pixels.
    private static final int SHIFT_LEFT = 12;
    private static final int SHIFT_UP = 20;

    private static final int COLOR_FULL = 0xFFFFFFFF;
    private static final int COLOR_EMPTY = 0x40808080; // ~25% alpha, desaturated
    private static final int COLOR_BOOST = 0xFFFFD028;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (mc.gameMode != null) {
            GameType mode = mc.gameMode.getPlayerMode();
            if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
                return;
            }
        }

        float energy = ClientEnergyState.getEnergy();

        // Segments span 0..MAX_ENERGY, so REST_CAP fills 10 of 12; the rest is boost headroom.
        float totalFraction = Math.max(0.0F, Math.min(1.0F, energy / MAX_ENERGY));
        int filledCount = Math.round(totalFraction * SEGMENT_COUNT);

        // Segments at or after this index are boost segments.
        int restSegmentCount = Math.round((REST_CAP / MAX_ENERGY) * SEGMENT_COUNT);

        float boostFraction = 0.0F;
        if (energy > REST_CAP) {
            boostFraction = Math.max(0.0F, Math.min(1.0F, (energy - REST_CAP) / (MAX_ENERGY - REST_CAP)));
        }

        float flash = com.terrainlab.client.ClientStatFlash.energy();
        int flashColor = com.terrainlab.client.ClientStatFlash.goldColor(flash);

        int scaledFrameH = Math.round(FRAME_H * SCALE);

        // Armor bar anchor (guiWidth()/2 - 91), above the hotbar, shifted by SHIFT_*.
        int baseX = graphics.guiWidth() / 2 - 91 - SHIFT_LEFT;
        int baseY = graphics.guiHeight() - 22 - scaledFrameH - 2 - SHIFT_UP;

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) baseX, (float) baseY);
        graphics.pose().scale(SCALE, SCALE);

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            int frameU = i * FRAME_W;
            int x = i * FRAME_W; // unscaled local coords; the pose scale shrinks them

            int color;
            if (i < filledCount) {
                color = i >= restSegmentCount ? lerpColor(COLOR_FULL, COLOR_BOOST, boostFraction) : COLOR_FULL;
            } else {
                color = COLOR_EMPTY;
            }

            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                    x, 0, (float) frameU, 0f, FRAME_W, FRAME_H,
                    FRAME_W * SEGMENT_COUNT, FRAME_H, color);

            // Golden pulse over the filled segments on a discrete energy gain.
            if (flash > 0.0F && i < filledCount) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, FLASH_TEXTURE,
                        x, 0, (float) frameU, 0f, FRAME_W, FRAME_H,
                        FRAME_W * SEGMENT_COUNT, FRAME_H, flashColor);
            }
        }

        graphics.pose().popMatrix();
    }

    private static int lerpColor(int from, int to, float t) {
        int fa = (from >>> 24) & 0xFF, fr = (from >>> 16) & 0xFF, fg = (from >>> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >>> 24) & 0xFF, tr = (to >>> 16) & 0xFF, tg = (to >>> 8) & 0xFF, tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
