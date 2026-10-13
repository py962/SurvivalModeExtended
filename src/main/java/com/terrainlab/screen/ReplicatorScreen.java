package com.terrainlab.screen;

import com.terrainlab.menu.ReplicatorMenu;
import com.terrainlab.network.ReplicatorPulsePacket;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Replicator bench screen. Draws the live redstone parts over a vanilla-style panel: dust-coloured
 * wires, repeaters, a lamp and a comparator readout, using vanilla block textures and the
 * redstone dust palette. Animation advances in discrete steps rather than smoothly.
 */
public class ReplicatorScreen extends AbstractContainerScreen<ReplicatorMenu> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("terrainlab", "textures/gui/replicator.png");
    private static final Identifier BUTTON = Identifier.fromNamespaceAndPath("terrainlab", "replicator/button");
    private static final Identifier BUTTON_PRESSED = Identifier.fromNamespaceAndPath("terrainlab", "replicator/button_pressed");
    private static final Identifier LIT_PROGRESS = Identifier.withDefaultNamespace("container/furnace/lit_progress");

    private static final Identifier LAMP = Identifier.withDefaultNamespace("textures/block/redstone_lamp.png");
    private static final Identifier LAMP_ON = Identifier.withDefaultNamespace("textures/block/redstone_lamp_on.png");
    private static final Identifier REPEATER = Identifier.withDefaultNamespace("textures/block/repeater.png");
    private static final Identifier REPEATER_ON = Identifier.withDefaultNamespace("textures/block/repeater_on.png");
    private static final Identifier COMPARATOR = Identifier.withDefaultNamespace("textures/block/comparator.png");
    private static final Identifier COMPARATOR_ON = Identifier.withDefaultNamespace("textures/block/comparator_on.png");

    /** Redstone dust colour by power 0-15, same formula as RedStoneWireBlock. */
    private static final int[] DUST = new int[16];

    static {
        for (int i = 0; i <= 15; i++) {
            float f = i / 15f;
            float r = f * 0.6f + (f > 0f ? 0.4f : 0.3f);
            float g = Mth.clamp(f * f * 0.7f - 0.5f, 0f, 1f);
            float b = Mth.clamp(f * f * 0.6f - 0.7f, 0f, 1f);
            DUST[i] = 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
        }
    }

    // Button area (recess at 22,32 - 39,49; sprite 14x14 drawn at 24,34).
    private static final int BTN_X = 24, BTN_Y = 34, BTN_SIZE = 14;
    private static final int PRESS_TICKS = 10; // client ticks the button stays pressed

    /** Wire paths as 2px-wide runs {x0, y0, x1, y1} (inclusive), in signal order, matching the background grooves. */
    private static final int[][] MAIN_PATH = {
            {73, 24, 74, 43},   // down from the input slot
            {75, 42, 110, 43},  // along to the output, under both repeater pads
    };
    private static final int[][] BUTTON_PATH = {
            {40, 40, 47, 41},   // out of the button
            {46, 24, 47, 39},   // up
            {48, 24, 54, 25},   // into the input slot
    };
    private static final int[][] LAMP_PATH = {
            {137, 42, 147, 43}, // output -> lamp
    };
    private static final int REPEATER_1_X = 82, REPEATER_2_X = 97, REPEATER_Y = 38;

    private int pressTicks = 0;
    private int clientTicks = 0;

    public ReplicatorScreen(ReplicatorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        clientTicks++;
        if (pressTicks > 0) {
            pressTicks--;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        boolean running = menu.isRunning();
        float progress = menu.getProgress();

        // Fuel flame (vanilla furnace sprite).
        if (menu.isBurning()) {
            int h = Mth.ceil(menu.getFuel() * 13f) + 1;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, LIT_PROGRESS, 14, 14, 0, 14 - h, x + 56, y + 36 + 14 - h, 14, h);
        }

        // Button -> input: lit by the GUI button (full power, briefly) or by a real incoming signal.
        int buttonPower = pressTicks > 0 ? 15 : menu.getInputPower();
        drawPath(g, BUTTON_PATH, buttonPower, 1f);

        // Input -> output: the signal front advances with progress in 2px steps.
        int mainLen = pathLength(MAIN_PATH);
        float stepped = running ? (float) Math.floor(progress * mainLen / 2f) * 2f / mainLen : 0f;
        drawPath(g, MAIN_PATH, running ? 14 : 0, stepped);

        // Repeaters light once the front has passed them.
        int front = (int) (stepped * mainLen);
        drawBlockIcon(g, x + REPEATER_1_X, y + REPEATER_Y, running && front > distanceTo(REPEATER_1_X + 4) ? REPEATER_ON : REPEATER, 10);
        drawBlockIcon(g, x + REPEATER_2_X, y + REPEATER_Y, running && front > distanceTo(REPEATER_2_X + 4) ? REPEATER_ON : REPEATER, 10);

        // Output -> lamp: lit while a batch runs.
        drawPath(g, LAMP_PATH, running ? 13 : 0, 1f);
        drawBlockIcon(g, x + 148, y + 35, running ? LAMP_ON : LAMP, 16);

        // Comparator + 15-cell readout.
        int signal = menu.getComparatorSignal();
        drawBlockIcon(g, x + 76, y + 58, signal > 0 ? COMPARATOR_ON : COMPARATOR, 10);
        for (int k = 0; k < 15; k++) {
            if (k < signal) {
                int cx = x + 90 + 3 * k;
                g.fill(cx, y + 61, cx + 2, y + 65, DUST[k + 1]);
            }
        }

        boolean pressed = pressTicks > 0;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, pressed ? BUTTON_PRESSED : BUTTON,
                x + BTN_X, y + BTN_Y, BTN_SIZE, BTN_SIZE);
    }

    /**
     * Draws a wire path at a redstone power level. {@code litFraction} of the
     * path (from its start) is drawn at {@code power}; the rest is drawn
     * unpowered (power 0 dust).
     */
    private void drawPath(GuiGraphicsExtractor g, int[][] path, int power, float litFraction) {
        int total = pathLength(path);
        int lit = Math.round(total * Mth.clamp(litFraction, 0f, 1f));
        int walked = 0;
        for (int[] seg : path) {
            boolean vertical = seg[0] + 1 >= seg[2];
            int len = vertical ? seg[3] - seg[1] + 1 : seg[2] - seg[0] + 1;
            for (int i = 0; i < len; i++, walked++) {
                int px = vertical ? seg[0] : seg[0] + i;
                int py = vertical ? seg[1] + i : seg[1];
                int colour = walked < lit ? DUST[power] : DUST[0];
                if (vertical) {
                    g.fill(leftPos + px, topPos + py, leftPos + px + 2, topPos + py + 1, colour);
                } else {
                    g.fill(leftPos + px, topPos + py, leftPos + px + 1, topPos + py + 2, colour);
                }
            }
        }
    }

    private static int pathLength(int[][] path) {
        int n = 0;
        for (int[] seg : path) {
            boolean vertical = seg[0] + 1 >= seg[2];
            n += vertical ? seg[3] - seg[1] + 1 : seg[2] - seg[0] + 1;
        }
        return n;
    }

    /** Distance along MAIN_PATH to a point on its horizontal run. */
    private static int distanceTo(int xOnMainRun) {
        int down = MAIN_PATH[0][3] - MAIN_PATH[0][1] + 1;
        return down + (xOnMainRun - MAIN_PATH[1][0]);
    }

    /** Draws a vanilla 16x16 block texture scaled to size x size. */
    private static void drawBlockIcon(GuiGraphicsExtractor g, int x, int y, Identifier texture, int size) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, size, size, 16, 16, 16, 16);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (isOverButton(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(font,
                    Component.translatable("container.terrainlab.replicator.pulse"), mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        double bx = leftPos + BTN_X, by = topPos + BTN_Y;
        return mouseX >= bx && mouseX < bx + BTN_SIZE && mouseY >= by && mouseY < by + BTN_SIZE;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean consumed) {
        if (!consumed && event.button() == 0 && isOverButton(event.x(), event.y())) {
            pressTicks = PRESS_TICKS;
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F));
            }
            ClientPacketDistributor.sendToServer(new ReplicatorPulsePacket());
            return true;
        }
        return super.mouseClicked(event, consumed);
    }
}
