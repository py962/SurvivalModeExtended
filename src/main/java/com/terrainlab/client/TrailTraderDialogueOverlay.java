package com.terrainlab.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;

/**
 * Draws the current Trail Trader dialogue line ({@link ClientTrailTraderDialogueState})
 * as a panel over the vanilla MerchantScreen.
 * <p>
 * The trade screen is not subclassed: MenuScreens maps one screen per MenuType, and
 * Trail Traders share MenuType.MERCHANT with villagers, so an overlay drawn from
 * ScreenEvent.Render.Post is used instead. TerrainLabClient#onClientSetup registers
 * {@link #onScreenRenderPost} manually on the game bus.
 */
public class TrailTraderDialogueOverlay {

    private static final int MAX_TEXT_WIDTH = 260;
    private static final int PANEL_PADDING = 6;
    private static final int PANEL_TOP_MARGIN = 10;
    private static final int PANEL_COLOR = 0xD0100C08;
    private static final int PANEL_BORDER = 0xFF5C4A2A;
    private static final int TEXT_COLOR = 0xFFEFE0BE;

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MerchantScreen)) {
            return;
        }

        String key = ClientTrailTraderDialogueState.getCurrentLineKey();
        if (key == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) {
            return;
        }

        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Component line = Component.translatable(key);
        List<FormattedCharSequence> wrapped = mc.font.split(line, MAX_TEXT_WIDTH);
        if (wrapped.isEmpty()) {
            return;
        }

        int lineHeight = mc.font.lineHeight;
        int panelWidth = MAX_TEXT_WIDTH + PANEL_PADDING * 2;
        int panelHeight = wrapped.size() * lineHeight + PANEL_PADDING * 2;
        int panelX = (event.getScreen().width - panelWidth) / 2;
        int panelY = PANEL_TOP_MARGIN;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_COLOR);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, PANEL_BORDER);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, PANEL_BORDER);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BORDER);

        int textY = panelY + PANEL_PADDING;
        for (FormattedCharSequence sequence : wrapped) {
            int lineWidth = mc.font.width(sequence);
            int textX = panelX + (panelWidth - lineWidth) / 2;
            graphics.text(mc.font, sequence, textX, textY, TEXT_COLOR, true);
            textY += lineHeight;
        }
    }
}
