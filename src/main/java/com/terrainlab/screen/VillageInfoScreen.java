package com.terrainlab.screen;

import com.terrainlab.menu.VillageInfoMenu;
import com.terrainlab.village.VillageTier;
import com.terrainlab.village.VillageTierRequirements;
import com.terrainlab.village.VillageInfoSnapshot;
import com.terrainlab.village.VillagerThermalState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Village info panel opened from the bell: summary lines (population and tier, happiness and trade points,
 * next-tier requirements coloured met/unmet) above a scrollable villager list. The list shows as many rows
 * as fit (up to {@code MAX_VISIBLE_ROWS}); trimmed cells show their full text as a tooltip.
 */
public class VillageInfoScreen extends AbstractContainerScreen<VillageInfoMenu> {

    private static final int PANEL_WIDTH = 280;
    private static final int PADDING = 8;
    private static final int LINE = 11;                 // summary line spacing

    private static final int LIST_HEADER_Y = 58;        // from topPos
    private static final int LIST_TOP_Y = LIST_HEADER_Y + 12;
    private static final int ROW_HEIGHT = 11;
    private static final int MIN_VISIBLE_ROWS = 4;
    private static final int MAX_VISIBLE_ROWS = 20;

    private static final int SCROLLBAR_WIDTH = 6;
    private static final int COLUMN_GAP = 6;
    private static final int PROFESSION_WIDTH = 108;
    private static final int LEVEL_WIDTH = 22;

    private int visibleRows;
    private int scrollOffset;
    private boolean draggingThumb;
    private double dragStartY;
    private int dragStartOffset;

    public VillageInfoScreen(VillageInfoMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        // No inventory slots, so hide the vanilla "Inventory" label.
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        // Size the list to the screen before the base class centres the panel.
        int rows = Math.max(rows().size(), 1);
        int fit = (this.height - 20 - LIST_TOP_Y - PADDING) / ROW_HEIGHT;
        this.visibleRows = Mth.clamp(Math.min(rows, fit), Math.min(rows, MIN_VISIBLE_ROWS), MAX_VISIBLE_ROWS);
        this.imageHeight = LIST_TOP_Y + this.visibleRows * ROW_HEIGHT + PADDING;
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxScroll());

        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 6;
    }

    private List<VillageInfoSnapshot.Row> rows() {
        return this.menu.getSnapshot().villagers();
    }

    private int maxScroll() {
        return Math.max(0, rows().size() - this.visibleRows);
    }

    private boolean needsScrollbar() {
        return maxScroll() > 0;
    }

    // Layout helpers, in screen coordinates.

    private int listLeft() {
        return this.leftPos + PADDING;
    }

    private int listRight() {
        return this.leftPos + this.imageWidth - PADDING - (needsScrollbar() ? SCROLLBAR_WIDTH + 4 : 0);
    }

    private int listTop() {
        return this.topPos + LIST_TOP_Y;
    }

    private int listBottom() {
        return listTop() + this.visibleRows * ROW_HEIGHT;
    }

    private int levelX() {
        return listLeft() + PROFESSION_WIDTH + COLUMN_GAP;
    }

    private int statusX() {
        return levelX() + LEVEL_WIDTH + COLUMN_GAP;
    }

    private int scrollbarX() {
        return this.leftPos + this.imageWidth - PADDING - SCROLLBAR_WIDTH;
    }

    private int thumbHeight() {
        int track = listBottom() - listTop();
        return Math.max(12, track * this.visibleRows / Math.max(1, rows().size()));
    }

    private int thumbY() {
        int track = listBottom() - listTop() - thumbHeight();
        return listTop() + (maxScroll() == 0 ? 0 : track * this.scrollOffset / maxScroll());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int right = left + this.imageWidth;
        int bottom = top + this.imageHeight;

        graphics.fill(left, top, right, bottom, 0xE0101010);
        graphics.fill(left, top, right, top + 1, 0xFF808080);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF808080);
        graphics.fill(left, top, left + 1, bottom, 0xFF808080);
        graphics.fill(right - 1, top, right, bottom, 0xFF808080);

        VillageInfoSnapshot.Snapshot snapshot = this.menu.getSnapshot();

        int y = top + 20;
        int summaryX = left + PADDING;
        graphics.drawString(this.font,
                "Villagers: " + snapshot.population() + "    Tier: " + tierLabel(snapshot.tier()),
                summaryX, y, 0xFFFFFFFF, false);
        y += LINE;
        graphics.drawString(this.font,
                "Happy: " + percent(snapshot.happiness()) + " now"
                        + (snapshot.dayAverageHappiness() >= 0.0 ? ", " + percent(snapshot.dayAverageHappiness()) + " today" : "")
                        + "    Points: " + snapshot.tradeQuality(),
                summaryX, y, 0xFFFFFFFF, false);
        y += LINE;
        drawNextTier(graphics, snapshot, summaryX, y);

        int headerY = top + LIST_HEADER_Y;
        graphics.drawString(this.font, "Profession", listLeft(), headerY, 0xFFA0A0A0, false);
        graphics.drawString(this.font, "Lvl", levelX(), headerY, 0xFFA0A0A0, false);
        graphics.drawString(this.font, "Status", statusX(), headerY, 0xFFA0A0A0, false);
        graphics.fill(left + PADDING, headerY + 10, right - PADDING, headerY + 11, 0xFF505050);

        List<VillageInfoSnapshot.Row> rows = rows();
        if (rows.isEmpty()) {
            graphics.drawString(this.font, "(no villagers found)", listLeft(), listTop() + 1, 0xFF808080, false);
        } else {
            int professionWidth = PROFESSION_WIDTH;
            int statusWidth = listRight() - statusX();
            int end = Math.min(rows.size(), this.scrollOffset + this.visibleRows);

            for (int i = this.scrollOffset; i < end; i++) {
                VillageInfoSnapshot.Row row = rows.get(i);
                int rowY = listTop() + (i - this.scrollOffset) * ROW_HEIGHT;

                if (i % 2 == 1) {
                    graphics.fill(listLeft() - 2, rowY - 1, listRight() + 2, rowY + ROW_HEIGHT - 1, 0x20FFFFFF);
                }

                String profession = professionName(row.professionKey());
                String status = statusText(row);
                String shownProfession = fit(profession, professionWidth);
                String shownStatus = fit(status, statusWidth);

                graphics.drawString(this.font, shownProfession, listLeft(), rowY + 1, 0xFFFFFFFF, false);
                graphics.drawString(this.font, String.valueOf(row.level()), levelX(), rowY + 1, 0xFFFFFFFF, false);
                graphics.drawString(this.font, shownStatus, statusX(), rowY + 1, statusColor(row), false);

                // Full text on hover when a column had to be trimmed.
                boolean hoverRow = mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 1;
                if (hoverRow && mouseX >= listLeft() && mouseX < levelX() && !shownProfession.equals(profession)) {
                    graphics.setTooltipForNextFrame(Component.literal(profession), mouseX, mouseY);
                } else if (hoverRow && mouseX >= statusX() && mouseX < listRight() && !shownStatus.equals(status)) {
                    graphics.setTooltipForNextFrame(Component.literal(status), mouseX, mouseY);
                }
            }
        }

        if (needsScrollbar()) {
            int x = scrollbarX();
            graphics.fill(x, listTop(), x + SCROLLBAR_WIDTH, listBottom(), 0xFF303030);
            int thumbTop = thumbY();
            boolean hot = this.draggingThumb || overThumb(mouseX, mouseY);
            graphics.fill(x, thumbTop, x + SCROLLBAR_WIDTH, thumbTop + thumbHeight(), hot ? 0xFFC0C0C0 : 0xFF909090);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFFFF, false);
        if (needsScrollbar()) {
            String range = (this.scrollOffset + 1) + "-" + (this.scrollOffset + this.visibleRows) + " of " + rows().size();
            graphics.drawString(this.font, range, this.imageWidth - PADDING - this.font.width(range), 20, 0xFF808080, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (needsScrollbar() && scrollY != 0.0) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - (int) Math.signum(scrollY) * 3, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && needsScrollbar()) {
            int x = scrollbarX();
            if (event.x() >= x && event.x() < x + SCROLLBAR_WIDTH
                    && event.y() >= listTop() && event.y() < listBottom()) {
                if (!overThumb(event.x(), event.y())) {
                    // Click on the track: centre the thumb there.
                    jumpTo(event.y());
                }
                this.draggingThumb = true;
                this.dragStartY = event.y();
                this.dragStartOffset = this.scrollOffset;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingThumb) {
            int track = listBottom() - listTop() - thumbHeight();
            if (track > 0) {
                double rowsPerPixel = maxScroll() / (double) track;
                this.scrollOffset = Mth.clamp(
                        this.dragStartOffset + (int) Math.round((event.y() - this.dragStartY) * rowsPerPixel),
                        0, maxScroll());
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingThumb && event.button() == 0) {
            this.draggingThumb = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean overThumb(double mouseX, double mouseY) {
        int x = scrollbarX();
        int y = thumbY();
        return mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= y && mouseY < y + thumbHeight();
    }

    private void jumpTo(double mouseY) {
        int track = listBottom() - listTop() - thumbHeight();
        if (track <= 0) {
            return;
        }
        double fraction = (mouseY - listTop() - thumbHeight() / 2.0) / track;
        this.scrollOffset = Mth.clamp((int) Math.round(fraction * maxScroll()), 0, maxScroll());
    }

    /** Trim to a pixel width, adding "..." if anything was cut. */
    private String fit(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("..."))) + "...";
    }

    /** "minecraft:weaponsmith" -> "Weaponsmith", "minecraft:none" -> "Unemployed". */
    private static String professionName(String key) {
        if (key == null || key.isEmpty()) {
            return "?";
        }
        String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        if (path.equals("none")) {
            return "Unemployed";
        }
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.toString();
    }

    /**
     * Draws "Next (T3): 16 villagers, 65% happy, 15 pts", each figure green when met. These are the
     * promotion thresholds the daily tier check uses.
     */
    private void drawNextTier(GuiGraphics graphics, VillageInfoSnapshot.Snapshot snapshot, int x, int y) {
        VillageTier tier = snapshot.tier();
        if (tier == VillageTier.TIER_4_TOWN) {
            graphics.drawString(this.font, "Top tier reached", x, y, 0xFF55FF55, false);
            return;
        }
        VillageTier next = tier.next();
        VillageTierRequirements req = VillageTierRequirements.forTier(next);
        int grey = 0xFFA0A0A0;
        x = drawPart(graphics, "Next (T" + next.level() + "): ", x, y, grey);
        x = drawPart(graphics, req.promotionPopulation() + " villagers",
                x, y, metColor(snapshot.population() >= req.promotionPopulation()));
        x = drawPart(graphics, ", ", x, y, grey);
        // Promotion is judged on the day's average happiness once one exists.
        double judged
 = snapshot.dayAverageHappiness() >= 0.0 ? snapshot.dayAverageHappiness() : snapshot.happiness();
        x = drawPart(graphics, percent(req.promotionHappiness()) + " happy",
                x, y, metColor(judged >= req.promotionHappiness()));
        x = drawPart(graphics, ", ", x, y, grey);
        drawPart(graphics, req.promotionTradeQuality() + " pts",
                x, y, metColor(snapshot.tradeQuality() >= req.promotionTradeQuality()));
    }

    private int drawPart(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(this.font, text, x, y, color, false);
        return x + this.font.width(text);
    }

    private static int metColor(boolean met) {
        return met ? 0xFF55FF55 : 0xFFFF6060;
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 100.0) + "%";
    }

    private static String tierLabel(com.terrainlab.village.VillageTier tier) {
        return "T" + tier.level() + " (" + tier.name() + ")";
    }

    private static String statusText(VillageInfoSnapshot.Row row) {
        List<String> parts = new ArrayList<>();
        if (row.scared()) parts.add("Scared");
        if (row.tired()) parts.add("Tired");
        if (row.trapped()) parts.add("Trapped");
        if (row.hungry()) parts.add("Hungry");
        if (row.thermalState() == VillagerThermalState.COLD) parts.add("Cold");
        if (row.thermalState() == VillagerThermalState.HOT) parts.add("Hot");
        return parts.isEmpty() ? "OK" : String.join(", ", parts);
    }

    private static int statusColor(VillageInfoSnapshot.Row row) {
        if (row.scared()) return 0xFFFF5555;
        if (row.tired()) return 0xFFFFAA00;
        if (row.trapped()) return 0xFFAA55FF;
        if (row.hungry()) return 0xFFD4A04C;
        if (row.thermalState() == VillagerThermalState.COLD) return 0xFF55AAFF;
        if (row.thermalState() == VillagerThermalState.HOT) return 0xFFFF8855;
        return 0xFF55FF55;
    }

}
