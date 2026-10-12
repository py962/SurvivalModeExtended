package com.terrainlab.screen;

import com.terrainlab.menu.CelestialForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Minimal Celestial Forge screen: one gem slot, showing faint gem outlines while empty, under a
 * rippling aurora strip. Drawn with fills and vanilla slot sprites, so it needs no texture.
 */
public class CelestialForgeScreen extends AbstractContainerScreen<CelestialForgeMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;
    private static final int SLOT_BG = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;

    // Vanilla's empty-slot gem outlines, cycled like the smithing table's.
    private static final List<Identifier> GEM_ICONS = List.of(
            Identifier.withDefaultNamespace("container/slot/diamond"),
            Identifier.withDefaultNamespace("container/slot/emerald"),
            Identifier.withDefaultNamespace("container/slot/amethyst_shard"),
            Identifier.withDefaultNamespace("container/slot/lapis_lazuli"),
            Identifier.withDefaultNamespace("container/slot/quartz"));

    private final CyclingSlotBackground gemIcon = new CyclingSlotBackground(0);

    public CelestialForgeScreen(CelestialForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = CelestialForgeMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        gemIcon.tick(GEM_ICONS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, OUTLINE);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
        g.fill(x + 1, y + 1, x + imageWidth - 2, y + 3, LIGHT);
        g.fill(x + 1, y + 1, x + 3, y + imageHeight - 2, LIGHT);
        g.fill(x + 2, y + imageHeight - 3, x + imageWidth - 1, y + imageHeight - 1, SHADOW);
        g.fill(x + imageWidth - 3, y + 2, x + imageWidth - 1, y + imageHeight - 1, SHADOW);

        renderAurora(g, x + 7, y + 16, imageWidth - 14, 14);

        drawSlot(g, x + CelestialForgeMenu.GEM_X - 1, y + CelestialForgeMenu.GEM_Y - 1);
        gemIcon.render(menu, g, partialTick, leftPos, topPos);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + 7 + col * 18, y + CelestialForgeMenu.INVENTORY_Y - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(g, x + 7 + col * 18, y + CelestialForgeMenu.INVENTORY_Y + 57);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
    }

    private static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, LIGHT);
        g.fill(x, y, x + 17, y + 17, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
    }

    /** A thin rippling aurora curtain (green to violet). */
    private static void renderAurora(GuiGraphics g, int x0, int y0, int width, int height) {
        g.fill(x0, y0, x0 + width, y0 + height, 0xFF0B1030);
        float t = (Util.getMillis() % 100000L) / 1000f;
        for (int i = 0; i < width; i++) {
            float u = i / 18f;
            float wave = Mth.sin(u + t * 0.6f) * 0.5f + Mth.sin(u * 2.3f - t * 0.9f) * 0.3f;
            int bar = Mth.clamp(Math.round((wave + 0.8f) * height * 0.45f) + 2, 1, height);
            float hue = 0.5f + 0.5f * Mth.sin(i / 30f + t * 0.2f);
            int r = (int) Mth.lerp(hue, 0x3D, 0x9A), gr = (int) Mth.lerp(hue, 0xFF, 0x6B), b = (int) Mth.lerp(hue, 0x9A, 0xFF);
            for (int dy = 0; dy < bar; dy++) {
                int alpha = (int) (200 * (1f - dy / (float) bar));
                g.fill(x0 + i, y0 + height - 1 - dy, x0 + i + 1, y0 + height - dy, (alpha << 24) | (r << 16) | (gr << 8) | b);
            }
        }
    }
}
