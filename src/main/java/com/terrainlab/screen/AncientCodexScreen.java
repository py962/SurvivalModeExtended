package com.terrainlab.screen;

import com.terrainlab.client.CodexPipelines;
import com.terrainlab.menu.AncientCodexMenu;
import com.terrainlab.network.GlyphSelectionPacket;
import net.minecraft.util.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.joml.Matrix3x2fStack;

/**
 * Ancient Codex screen: an animated ring of eight glyph sprites with additive glow
 * ({@link CodexPipelines#GUI_GLOW}). Motion uses wall-clock time so it is frame-rate independent.
 * The success/failure animation is triggered by the server via {@link #onCodexResult}.
 */
public class AncientCodexScreen extends AbstractContainerScreen<AncientCodexMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/ancient_codex.png");
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("terrainlab", "codex/glow");
    private static final Identifier SPARK = Identifier.fromNamespaceAndPath("terrainlab", "codex/spark");
    private static final Identifier[] GLYPH_SPRITES = new Identifier[8];

    static {
        for (int i = 0; i < 8; i++) {
            GLYPH_SPRITES[i] = Identifier.fromNamespaceAndPath("terrainlab", "codex/glyph_" + i);
        }
    }

    // Glyph positions in the background texture (top-left, size); must match the sprite PNGs.
    private static final int[] SPRITE_X = {121, 151, 172, 153, 120, 89, 71, 89};
    private static final int[] SPRITE_Y = {9, 21, 51, 86, 101, 86, 51, 21};
    private static final int[] SPRITE_W = {13, 15, 11, 11, 15, 14, 10, 14};
    private static final int[] SPRITE_H = {15, 19, 18, 17, 18, 17, 18, 19};

    /** Click area: a 24x24 square centred on each glyph. */
    private static final int HIT_HALF = 12;

    /** Centre of the output slot (slot at 119,58 + 8). Glyphs fly here. */
    private static final float OUTPUT_CX = 127f;
    private static final float OUTPUT_CY = 66f;

    // Colours (RGB). Violet family to match the art.
    private static final int GLOW_VIOLET = 0x9A6BFF;
    private static final int GLOW_HOT = 0xE2D2FF;
    private static final int GLOW_FAIL = 0xFF5A6E;
    // Selected glyphs glow gold (hover stays violet).
    private static final int GOLD = 0xFFB52E;
    private static final int GOLD_HOT = 0xFFE49A;

    // Timings, seconds.
    private static final float FLIGHT_TIME = 0.9f;
    private static final float FLIGHT_STAGGER = 0.08f;
    private static final float BURST_TIME = 0.5f;
    private static final float FAIL_TIME = 0.7f;
    private static final float RETURN_FADE = 0.6f;

    // Eased per-glyph state (0..1).
    private final float[] hoverT = new float[8];
    private final float[] selectT = new float[8];

    // Server-driven result animation.
    private int resultMask = 0;
    private boolean resultSuccess = false;
    private double resultStart = -1;

    private double lastFrameTime = -1;

    public AncientCodexScreen(AncientCodexMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 222;
    }

    /** Called from CodexResultPacket on the client thread. */
    public void onCodexResult(boolean success, int glyphMask) {
        this.resultSuccess = success;
        this.resultMask = glyphMask;
        this.resultStart = now();
    }

    private static double now() {
        return Util.getMillis() / 1000.0;
    }

    private static float cx(int i) {
        return SPRITE_X[i] + SPRITE_W[i] / 2f;
    }

    private static float cy(int i) {
        return SPRITE_Y[i] + SPRITE_H[i] / 2f;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos,
                0.0F, 0.0F, imageWidth, imageHeight, 256, 222);

        double t = now();
        float dt = lastFrameTime < 0 ? 0f : (float) Math.min(0.1, t - lastFrameTime);
        lastFrameTime = t;

        // Read from the menu: the client block entity never receives the selection.
        int selectedMask = menu.getSelectedMask();
        float resultAge = resultStart < 0 ? Float.MAX_VALUE : (float) (t - resultStart);

        for (int i = 0; i < 8; i++) {
            boolean hovered = isOverGlyph(i, mouseX, mouseY);
            // Exponential ease towards target - frame-rate independent.
            hoverT[i] = approach(hoverT[i], hovered ? 1f : 0f, dt, 14f);
            selectT[i] = approach(selectT[i], (selectedMask & (1 << i)) != 0 ? 1f : 0f, dt, 10f);
            renderGlyph(graphics, i, (float) t, resultAge);
        }
    }

    private void renderGlyph(GuiGraphics g, int i, float t, float resultAge) {
        float hover = hoverT[i];
        float sel = selectT[i];
        boolean inResult = (resultMask & (1 << i)) != 0;

        float pulse = 0.5f + 0.5f * Mth.sin(t * 3.0f + i * 0.7f);
        float breathe = 0.5f + 0.5f * Mth.sin(t * 1.3f + i * 1.1f);

        // Gentle idle drift, each glyph on its own phase.
        float dx = 0f;
        float dy = Mth.sin(t * 1.6f + i * 0.8f) * 0.6f;
        float scale = 1f + 0.10f * hover + 0.06f * sel;
        float glyphAlpha = 0.82f + 0.18f * Math.max(hover, sel);
        int glowColour = GLOW_VIOLET;
        float failFlash = 0f;

        if (inResult) {
            if (resultSuccess) {
                // The glyph has flown out: its tile stays empty, then it fades back in.
                float back = (resultAge - (FLIGHT_TIME + FLIGHT_STAGGER * 3)) / RETURN_FADE;
                glyphAlpha *= Mth.clamp(back, 0f, 1f);
                sel = 0f;
            } else if (resultAge < FAIL_TIME) {
                float p = resultAge / FAIL_TIME;
                float k = 1f - p;
                dx += Mth.sin(resultAge * 70f) * 1.6f * k;          // shake
                failFlash = k * (0.6f + 0.4f * Mth.sin(resultAge * 40f)); // flicker
                glowColour = GLOW_FAIL;
                glyphAlpha *= 0.55f + 0.45f * p;                     // dim, recover
            }
        }

        float x = leftPos + cx(i) + dx;
        float y = topPos + cy(i) + dy;

        // Energy flowing along the etched line to the centre while selected.
        if (sel > 0.01f) {
            renderEnergyLine(g, x, y, i, t, sel);
        }

        // Soft halo: faint violet breathing when idle, stronger on hover;
        // a selected glyph gets a gold halo on top.
        float haloAlpha = 0.07f + 0.05f * breathe + 0.22f * hover + 0.6f * failFlash;
        drawGlow(g, x, y, 30f + 4f * hover, glowColour, haloAlpha * (1f - 0.6f * sel));
        if (sel > 0.01f) {
            drawGlow(g, x, y, 34f, GOLD, 0.55f * sel * (0.75f + 0.25f * pulse));
            // Tight hot core behind a selected glyph.
            drawGlow(g, x, y, 16f, GOLD_HOT, 0.35f * sel * (0.7f + 0.3f * pulse));
        }

        if (glyphAlpha > 0.01f) {
            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale, scale);
            int w = SPRITE_W[i], h = SPRITE_H[i];
            int ox = -w / 2, oy = -h / 2;
            // The glyph itself.
            g.blitSprite(RenderPipelines.GUI_TEXTURED, GLYPH_SPRITES[i], ox, oy, w, h,
                    argb(glyphAlpha, 0xFFFFFF));
            // Additive copies make the strokes glow: pale violet on hover, gold when selected.
            float hoverLight = 0.15f * hover * (1f - sel) + 0.5f * failFlash;
            if (hoverLight > 0.01f) {
                g.blitSprite(CodexPipelines.GUI_GLOW, GLYPH_SPRITES[i], ox, oy, w, h,
                        argb(Math.min(1f, hoverLight) * glyphAlpha, failFlash > 0 ? GLOW_FAIL : GLOW_HOT));
            }
            float goldLight = 0.75f * sel * (0.7f + 0.3f * pulse);
            if (goldLight > 0.01f) {
                g.blitSprite(CodexPipelines.GUI_GLOW, GLYPH_SPRITES[i], ox, oy, w, h,
                        argb(Math.min(1f, goldLight) * glyphAlpha, GOLD_HOT));
            }
            pose.popMatrix();
        }

        // Two motes orbiting a selected glyph.
        if (sel > 0.01f) {
            for (int k = 0; k < 2; k++) {
                float a = t * 2.2f + k * Mth.PI + i;
                float r = 11f + Mth.sin(t * 1.7f + k) * 1.5f;
                drawSpark(g, x + Mth.cos(a) * r, y + Mth.sin(a) * r * 0.8f, 0.8f, GOLD_HOT, 0.8f * sel);
            }
        }
    }

    /** Sparks travelling from the glyph's tile towards the centre along the etched connector. */
    private void renderEnergyLine(GuiGraphics g, float x, float y, int i, float t, float sel) {
        float tx = leftPos + OUTPUT_CX, ty = topPos + OUTPUT_CY;
        float len = Mth.sqrt((tx - x) * (tx - x) + (ty - y) * (ty - y));
        // Start just outside the glyph tile, stop at the centre box's edge.
        float from = 14f / len, to = 1f - 14f / len;
        for (int k = 0; k < 3; k++) {
            float f = frac(t * 0.9f + k / 3f + i * 0.13f);
            float s = Mth.lerp(f, from, to);
            float fade = Mth.sin(f * Mth.PI); // fade in at the tile, out at the centre
            drawSpark(g, Mth.lerp(s, x, tx), Mth.lerp(s, y, ty), 0.7f, GOLD, 0.9f * sel * fade);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (resultStart >= 0) {
            renderResultAnimation(graphics, (float) (now() - resultStart));
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Success: glyphs lift off, curve into the output slot trailing sparks, then a burst. */
    private void renderResultAnimation(GuiGraphics g, float age) {
        if (!resultSuccess) {
            if (age > FAIL_TIME) {
                resultStart = -1;
                resultMask = 0;
            }
            return; // failure is drawn in-place by renderGlyph
        }

        float total = FLIGHT_TIME + FLIGHT_STAGGER * 3 + Math.max(BURST_TIME, RETURN_FADE);
        if (age > total) {
            resultStart = -1;
            resultMask = 0;
            return;
        }

        float tx = leftPos + OUTPUT_CX, ty = topPos + OUTPUT_CY;
        int n = 0;
        for (int i = 0; i < 8; i++) {
            if ((resultMask & (1 << i)) == 0) {
                continue;
            }
            float local = (age - FLIGHT_STAGGER * n++) / FLIGHT_TIME;
            if (local <= 0f || local >= 1f) {
                continue;
            }
            float sx = leftPos + cx(i), sy = topPos + cy(i);
            // Control point: lifted outward from the centre so the path arcs.
            float mx = (sx + tx) / 2f + (sx - tx) * 0.45f;
            float my = (sy + ty) / 2f + (sy - ty) * 0.45f - 10f;

            // Trail: earlier positions along the same curve, fading.
            for (int k = 5; k >= 1; k--) {
                float tt = local - k * 0.045f;
                if (tt <= 0f) {
                    continue;
                }
                float e = easeInCubic(tt);
                drawSpark(g, bez(sx, mx, tx, e), bez(sy, my, ty, e), 0.9f - k * 0.1f,
                        GLOW_VIOLET, 0.55f * (1f - k / 6f));
            }

            float e = easeInCubic(local);
            float x = bez(sx, mx, tx, e), y = bez(sy, my, ty, e);
            float s = Mth.lerp(e, 1.1f, 0.35f);
            drawGlow(g, x, y, 22f * s + 6f, GLOW_VIOLET, 0.7f);

            Matrix3x2fStack pose = g.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(s, s);
            int w = SPRITE_W[i], h = SPRITE_H[i];
            g.blitSprite(RenderPipelines.GUI_TEXTURED, GLYPH_SPRITES[i], -w / 2, -h / 2, w, h, 0xFFFFFFFF);
            g.blitSprite(CodexPipelines.GUI_GLOW, GLYPH_SPRITES[i], -w / 2, -h / 2, w, h, argb(0.9f, GLOW_HOT));
            pose.popMatrix();
        }

        // Burst when the last glyph arrives.
        float arrive = FLIGHT_TIME + FLIGHT_STAGGER * Math.max(0, n - 1);
        float b = (age - arrive + 0.05f) / BURST_TIME;
        if (b > 0f && b < 1f) {
            float fade = 1f - b;
            drawGlow(g, tx, ty, Mth.lerp(easeOutCubic(b), 12f, 70f), GLOW_VIOLET, 0.9f * fade);
            drawGlow(g, tx, ty, Mth.lerp(easeOutCubic(b), 8f, 30f), GLOW_HOT, fade);
            for (int k = 0; k < 10; k++) {
                float a = k * (Mth.TWO_PI / 10f) + 0.3f;
                float r = easeOutCubic(b) * 26f;
                drawSpark(g, tx + Mth.cos(a) * r, ty + Mth.sin(a) * r, 1f - b * 0.5f, GLOW_HOT, fade);
            }
        }
    }

    /** Additive soft glow centred on (x, y), {@code size} GUI pixels across. */
    private static void drawGlow(GuiGraphics g, float x, float y, float size, int rgb, float alpha) {
        if (alpha <= 0.005f) {
            return;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(size / 32f, size / 32f);
        g.blitSprite(CodexPipelines.GUI_GLOW, GLOW, -16, -16, 32, 32, argb(Math.min(1f, alpha), rgb));
        pose.popMatrix();
    }

    /** Additive 7x7 spark centred on (x, y). */
    private static void drawSpark(GuiGraphics g, float x, float y, float scale, int rgb, float alpha) {
        if (alpha <= 0.005f) {
            return;
        }
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        g.blitSprite(CodexPipelines.GUI_GLOW, SPARK, -3, -3, 7, 7, argb(Math.min(1f, alpha), rgb));
        pose.popMatrix();
    }

    private static int argb(float alpha, int rgb) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static float approach(float cur, float target, float dt, float rate) {
        return cur + (target - cur) * (1f - (float) Math.exp(-rate * dt));
    }

    private static float bez(float a, float b, float c, float t) {
        float u = 1f - t;
        return u * u * a + 2f * u * t * b + t * t * c;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private boolean isOverGlyph(int i, double mouseX, double mouseY) {
        float x = leftPos + cx(i), y = topPos + cy(i);
        return mouseX >= x - HIT_HALF && mouseX < x + HIT_HALF
                && mouseY >= y - HIT_HALF && mouseY < y + HIT_HALF;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        if (!consumed && event.button() == 0) {
            for (int i = 0; i < 8; i++) {
                if (isOverGlyph(i, event.x(), event.y())) {
                    handleGlyphClick(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, consumed);
    }

    private void handleGlyphClick(int index) {
        int mask = menu.getSelectedMask();
        boolean alreadySelected = (mask & (1 << index)) != 0;
        // Deselect, or select when fewer than 3 are chosen.
        if (alreadySelected || Integer.bitCount(mask) < 3) {
            ClientPacketDistributor.sendToServer(new GlyphSelectionPacket(index));
            menu.predictToggle(index); // highlight now, server confirms
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // No labels: the title would overlap the artwork.
    }
}
