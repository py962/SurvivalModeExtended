package com.terrainlab.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.terrainlab.TerrainLab;
import com.terrainlab.advancement.Accomplishment;
import com.terrainlab.advancement.AccomplishmentCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen accomplishment card shown after returning from the End, laid out for a screenshot:
 * the big gold title, a badge (vanilla challenge frame + icon + name) for every other
 * accomplishment the player holds, the player's face, name, mode and world, six run statistics
 * and the mod name and version.
 * <p>
 * The card is laid out at a fixed logical width and scaled as a whole to fit the window (down when
 * it does not fit, up by whole steps when there is room). The HUD is hidden while it is open
 * (advancement toasts are held back by AccomplishmentCardClient). Any click or key closes it, except the screenshot, hide-HUD, fullscreen and debug keys
 * and bare modifiers, so F2 can capture it; input in the first {@link #INPUT_DELAY_MS} is ignored so
 * a held key does not dismiss it immediately.
 */
public class AccomplishmentScreen extends Screen {

    private static final Identifier CHALLENGE_FRAME =
            Identifier.withDefaultNamespace("advancements/challenge_frame_obtained");

    private static final int PANEL_WIDTH = 320;
    private static final int PAD = 12;
    private static final int LINE = 10;
    private static final int FRAME = 26;
    private static final int BADGE_SLOT = 84;
    private static final int BADGE_HEIGHT = FRAME + 3 + LINE + LINE;
    private static final int FACE = 32;
    private static final int STAT_ROW = 24;
    private static final float MAX_TITLE_SCALE = 3.0F;
    private static final float MIN_TITLE_SCALE = 1.5F;
    private static final long INPUT_DELAY_MS = 600L;
    private static final long HINT_VISIBLE_MS = 3000L;
    private static final long HINT_FADE_MS = 1000L;

    private static final int GOLD = 0xFFFFC94A;
    private static final int GOLD_DARK = 0xFF7A5A14;
    private static final int BORDER = 0xFFC9A227;
    private static final int PANEL_BG = 0xF0120E08;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int DARK_GREY = 0xFF707070;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF7CFC7C;

    private static String cachedVersion;

    private final AccomplishmentCard card;
    private final Accomplishment titleAccomplishment;
    private final List<Accomplishment> badges = new ArrayList<>();
    private final long openedAt = Util.getMillis();
    private boolean hidHud;

    private int panelHeight;
    private float titleScale;
    private float scale;
    private int panelLeft;
    private int panelTop;

    public AccomplishmentScreen(AccomplishmentCard card) {
        super(Component.translatable("gui.terrainlab.accomplishment.screen"));
        this.card = card;
        Accomplishment title = Accomplishment.byId(card.title());
        this.titleAccomplishment = title != null ? title : Accomplishment.SURVIVOR;
        for (String id : card.earned()) {
            Accomplishment a = Accomplishment.byId(id);
            if (a != null && a != this.titleAccomplishment) {
                this.badges.add(a);
            }
        }
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void added() {
        super.added();
        Minecraft mc = Minecraft.getInstance();
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            this.hidHud = true;
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F));
    }

    @Override
    public void removed() {
        Minecraft mc = Minecraft.getInstance();
        if (this.hidHud && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        this.hidHud = false;
        super.removed();
    }

    @Override
    protected void init() {
        Component title = Component.translatable(this.titleAccomplishment.cardTitleKey());
        int titleWidth = Math.max(1, this.font.width(title));
        this.titleScale = Mth.clamp((PANEL_WIDTH - 2.0F * PAD) / titleWidth, MIN_TITLE_SCALE, MAX_TITLE_SCALE);

        this.panelHeight = layoutHeight();

        // Leave a margin and a line below the panel for the close hint.
        float fitW = (this.width - 16.0F) / PANEL_WIDTH;
        float fitH = (this.height - 16.0F - LINE) / this.panelHeight;
        float fit = Math.min(fitW, fitH);
        this.scale = fit >= 1.0F ? Math.min(2.0F, Mth.floor(fit)) : Math.max(0.25F, fit);

        this.panelLeft = Math.round((this.width - PANEL_WIDTH * this.scale) / 2.0F);
        this.panelTop = Math.round((this.height - LINE - this.panelHeight * this.scale) / 2.0F);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isPassThroughKey(event) || Util.getMillis() - this.openedAt < INPUT_DELAY_MS) {
            return false;
        }
        this.onClose();
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (Util.getMillis() - this.openedAt < INPUT_DELAY_MS) {
            return false;
        }
        this.onClose();
        return true;
    }

    /** Keys that must not close the card: F1/F2, their bindings, fullscreen, debug, bare modifiers. */
    private boolean isPassThroughKey(KeyEvent event) {
        Options options = this.minecraft.options;
        int key = event.key();
        return key == InputConstants.KEY_F1 || key == InputConstants.KEY_F2
                || options.keyScreenshot.matches(event) || options.keyToggleGui.matches(event)
                || options.keyFullscreen.matches(event)
                || options.keyDebugModifier.matches(event) || options.keyDebugOverlay.matches(event)
                || key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
                || key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
                || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
                || key == InputConstants.KEY_LGUI || key == InputConstants.KEY_RGUI;
    }

    // ---------------------------------------------------------------- layout

    private int titleTop() {
        return PAD + LINE + 6;
    }

    private int titleBottom() {
        return titleTop() + Mth.ceil(9 * this.titleScale);
    }

    private int badgesTop() {
        return titleBottom() + 13;
    }

    private int playerTop() {
        return badges.isEmpty() ? badgesTop() : badgesTop() + BADGE_HEIGHT + 9;
    }

    private int statsTop() {
        return playerTop() + 4 * LINE + 13;
    }

    private int footerTop() {
        return statsTop() + 3 * STAT_ROW + 6;
    }

    private int layoutHeight() {
        return footerTop() + LINE + PAD - 2;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xD8000000, 0xE8000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.pose().pushMatrix();
        graphics.pose().translate(this.panelLeft, this.panelTop);
        graphics.pose().scale(this.scale, this.scale);
        drawPanel(graphics);
        graphics.pose().popMatrix();

        drawCloseHint(graphics);
    }

    private void drawPanel(GuiGraphicsExtractor graphics) {
        int w = PANEL_WIDTH;
        int h = this.panelHeight;
        int cx = w / 2;

        graphics.fill(0, 0, w, h, PANEL_BG);
        graphics.outline(0, 0, w, h, BORDER);
        graphics.outline(2, 2, w - 4, h - 4, GOLD_DARK);

        // Header and title.
        graphics.centeredText(this.font, Component.translatable("gui.terrainlab.accomplishment.header"),
                cx, PAD, GREY);
        Component title = Component.translatable(this.titleAccomplishment.cardTitleKey());
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, titleTop());
        graphics.pose().scale(this.titleScale, this.titleScale);
        graphics.text(this.font, title, -this.font.width(title) / 2, 0, GOLD, true);
        graphics.pose().popMatrix();
        separator(graphics, titleBottom() + 6);

        // Badges.
        if (!this.badges.isEmpty()) {
            int left = cx - this.badges.size() * BADGE_SLOT / 2;
            int y = badgesTop();
            for (int k = 0; k < this.badges.size(); k++) {
                drawBadge(graphics, this.badges.get(k), left + k * BADGE_SLOT + BADGE_SLOT / 2, y);
            }
            separator(graphics, y + BADGE_HEIGHT + 3);
        }

        drawPlayer(graphics, playerTop());
        separator(graphics, statsTop() - 7);
        drawStats(graphics, statsTop());
        separator(graphics, footerTop() - 4);

        graphics.centeredText(this.font, Component.translatable("gui.terrainlab.accomplishment.footer", modVersion()),
                cx, footerTop() + 2, DARK_GREY);
    }

    /**
     * The mod's version from its mod container. Called reflectively: IModInfo.getVersion() returns a
     * maven-artifact type that is not on every compile classpath this project is checked with.
     */
    private static String modVersion() {
        if (cachedVersion == null) {
            cachedVersion = ModList.get().getModContainerById(TerrainLab.MODID).map(c -> {
                try {
                    Object version = net.neoforged.neoforgespi.language.IModInfo.class
                            .getMethod("getVersion").invoke(c.getModInfo());
                    return String.valueOf(version);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return "";
                }
            }).orElse("");
        }
        return cachedVersion;
    }

    private void separator(GuiGraphicsExtractor graphics, int y) {
        graphics.fill(PAD, y, PANEL_WIDTH - PAD, y + 1, GOLD_DARK);
    }

    private void drawBadge(GuiGraphicsExtractor graphics, Accomplishment badge, int centreX, int y) {
        int x = centreX - FRAME / 2;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CHALLENGE_FRAME, x, y, FRAME, FRAME);
        graphics.fakeItem(new ItemStack(badge.icon()), x + 5, y + 5);

        boolean fresh = this.card.fresh().contains(badge.id());
        Component name = Component.translatable(badge.nameKey());
        int maxWidth = BADGE_SLOT - 4;
        String text = this.font.plainSubstrByWidth(name.getString(), maxWidth);
        graphics.centeredText(this.font, text, centreX, y + FRAME + 3, fresh ? GOLD : WHITE);
        if (fresh) {
            graphics.centeredText(this.font, Component.translatable("gui.terrainlab.accomplishment.new"),
                    centreX, y + FRAME + 3 + LINE, GREEN);
        }
    }

    private void drawPlayer(GuiGraphicsExtractor graphics, int y) {
        Minecraft mc = Minecraft.getInstance();
        int faceX = PAD + 4;
        int faceY = y + (4 * LINE - FACE) / 2 - 1;
        if (mc.player != null) {
            graphics.fill(faceX - 1, faceY - 1, faceX + FACE + 1, faceY + FACE + 1, GOLD_DARK);
            PlayerFaceExtractor.extractRenderState(graphics, mc.player.getSkin(), faceX, faceY, FACE);
        }

        int x = faceX + FACE + 10;
        int maxWidth = PANEL_WIDTH - PAD - x;
        graphics.text(this.font, this.font.plainSubstrByWidth(this.card.playerName(), maxWidth), x, y, WHITE, true);

        Component mode = Component.translatable(this.card.hardcore()
                ? "gui.terrainlab.accomplishment.mode.ultra_hardcore" : "gui.terrainlab.accomplishment.mode.survival");
        Component modeLine = Component.translatable("gui.terrainlab.accomplishment.mode_line",
                mode, Difficulty.byId(this.card.difficulty()).getDisplayName());
        graphics.text(this.font, modeLine, x, y + LINE, this.card.hardcore() ? RED : GOLD, true);
        if (this.card.cheats()) {
            int after = x + this.font.width(modeLine) + 8;
            graphics.text(this.font, Component.translatable("gui.terrainlab.accomplishment.cheats"),
                    after, y + LINE, GREY, false);
        }

        String world = Component.translatable("gui.terrainlab.accomplishment.world", this.card.worldName()).getString();
        graphics.text(this.font, this.font.plainSubstrByWidth(world, maxWidth), x, y + 2 * LINE, GREY, false);
        graphics.text(this.font, Component.translatable("gui.terrainlab.accomplishment.seed",
                Long.toString(this.card.seed())), x, y + 3 * LINE, GREY, false);
    }

    private void drawStats(GuiGraphicsExtractor graphics, int y) {
        long seconds = this.card.playTicks() / 20L;
        Component playTime = Component.translatable("gui.terrainlab.accomplishment.hours_minutes",
                Long.toString(seconds / 3600L), Long.toString(seconds % 3600L / 60L));
        Locale locale = Minecraft.getInstance().getLanguageManager().getJavaLocale();
        Component distance = Component.translatable("gui.terrainlab.accomplishment.km",
                String.format(locale, "%.1f", this.card.distanceCm() / 100000.0));
        String date = LocalDate.ofEpochDay(this.card.dateEpochDay())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy", locale));

        int colWidth = (PANEL_WIDTH - 2 * PAD) / 2;
        int left = PAD + colWidth / 2;
        int right = PAD + colWidth + colWidth / 2;
        stat(graphics, "days", Component.literal(Long.toString(this.card.days())), left, y);
        stat(graphics, "play_time", playTime, right, y);
        stat(graphics, "deaths", Component.literal(Integer.toString(this.card.deaths())), left, y + STAT_ROW);
        stat(graphics, "mob_kills", Component.literal(Integer.toString(this.card.mobKills())), right, y + STAT_ROW);
        stat(graphics, "distance", distance, left, y + 2 * STAT_ROW);
        stat(graphics, "date", Component.literal(date), right, y + 2 * STAT_ROW);
    }

    private void stat(GuiGraphicsExtractor graphics, String key, Component value, int centreX, int y) {
        graphics.centeredText(this.font, Component.translatable("gui.terrainlab.accomplishment.stat." + key),
                centreX, y, GREY);
        graphics.text(this.font, value, centreX - this.font.width(value) / 2, y + LINE, WHITE, true);
    }

    /** "Click or press any key to close" below the panel, fading out so it is not in later screenshots. */
    private void drawCloseHint(GuiGraphicsExtractor graphics) {
        long shown = Util.getMillis() - this.openedAt;
        float alpha = 1.0F - Mth.clamp((shown - HINT_VISIBLE_MS) / (float) HINT_FADE_MS, 0.0F, 1.0F);
        int a = Math.round(alpha * 255.0F);
        if (a < 8) {
            return;
        }
        int y = Math.round(this.panelTop + this.panelHeight * this.scale) + 4;
        graphics.centeredText(this.font, Component.translatable("gui.terrainlab.accomplishment.close_hint"),
                this.width / 2, Math.min(y, this.height - LINE), (a << 24) | 0x909090);
    }
}
