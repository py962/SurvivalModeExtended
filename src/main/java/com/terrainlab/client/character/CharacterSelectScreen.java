package com.terrainlab.client.character;

import com.terrainlab.character.CharacterRegistry;
import com.terrainlab.character.CharacterSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The character screen shown every time an existing singleplayer world is opened from the world
 * list: one row per character (name, dimension, XP level, health, play time, last played; "Dead"
 * for a character that died in a hardcore world) with Play / Rename / Delete, plus
 * "New character" (while fewer than {@link CharacterRegistry#MAX_CHARACTERS} exist) and "Back".
 * <p>
 * Everything is read fresh from the world folder whenever the screen is (re)initialised. The
 * registry file is only written when the player acts (play, create, rename, delete), so merely
 * looking at the screen leaves the world folder untouched.
 */
public class CharacterSelectScreen extends Screen {

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int ROW_BG = 0x66000000;
    private static final int ROW_BG_HOVER = 0x99000000;
    private static final int ROW_BORDER = 0xFF808080;
    private static final int BUTTON_COLUMN = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault());

    private final WorldSelectionList.WorldListEntry worldEntry;
    private final LevelSummary summary;
    private final Path worldDir;
    private final Runnable back;

    private CharacterRegistry registry;
    private final List<Row> rows = new ArrayList<>();
    private int rowWidth;
    private int rowPitch;
    private int rowHeight;
    private int top;

    private record Row(CharacterRegistry.Entry character, CharacterFiles.Info info, int x, int y) {
    }

    public CharacterSelectScreen(WorldSelectionList.WorldListEntry worldEntry, LevelSummary summary, Path worldDir, Runnable back) {
        super(Component.translatable("terrainlab.character.title"));
        this.worldEntry = worldEntry;
        this.summary = summary;
        this.worldDir = worldDir;
        this.back = back;
    }

    @Override
    protected void init() {
        // Character 1 of a world without a list yet is the world's existing progress: the account's UUID.
        this.registry = CharacterRegistry.loadOrCreate(this.worldDir, this.minecraft.getUser().getProfileId(),
                this.minecraft.getUser().getName(), this.summary.getLastPlayed());
        this.rows.clear();

        this.rowWidth = Math.min(420, this.width - 20);
        this.top = 34;
        this.rowPitch = Math.max(42, Math.min(50, (this.height - this.top - 32) / CharacterRegistry.MAX_CHARACTERS));
        this.rowHeight = this.rowPitch - 4;
        int x = (this.width - this.rowWidth) / 2;
        boolean canDelete = this.registry.characters().size() > 1;

        int index = 0;
        for (CharacterRegistry.Entry character : this.registry.characters()) {
            int y = this.top + index * this.rowPitch;
            CharacterFiles.Info info = CharacterFiles.read(this.worldDir, character.uuid(), this.summary.isHardcore());
            this.rows.add(new Row(character, info, x, y));

            int bx = x + this.rowWidth - BUTTON_COLUMN - 3;
            int half = (BUTTON_COLUMN - 2) / 2;
            Component playLabel = Component.translatable(info.dead() ? "terrainlab.character.spectate" : "terrainlab.character.play");
            this.addRenderableWidget(Button.builder(playLabel, b -> this.play(character))
                    .bounds(bx, y + 2, BUTTON_COLUMN, 18).build());
            this.addRenderableWidget(Button.builder(Component.translatable("terrainlab.character.rename"), b -> this.rename(character))
                    .bounds(bx, y + 22, half, 16).build());
            Button delete = this.addRenderableWidget(Button.builder(Component.translatable("terrainlab.character.delete"), b -> this.confirmDelete(character))
                    .bounds(bx + BUTTON_COLUMN - half, y + 22, half, 16).build());
            delete.active = canDelete;
            if (!canDelete) delete.setTooltip(Tooltip.create(Component.translatable("terrainlab.character.delete.last")));
            index++;
        }

        int footerY = this.height - 26;
        Button create = this.addRenderableWidget(Button.builder(Component.translatable("terrainlab.character.new"), b -> this.create())
                .bounds(this.width / 2 - 154, footerY, 150, 20).build());
        create.active = this.registry.canAdd();
        if (!create.active) {
            create.setTooltip(Tooltip.create(Component.translatable("terrainlab.character.new.full", CharacterRegistry.MAX_CHARACTERS)));
        }
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
                .bounds(this.width / 2 + 4, footerY, 150, 20).build());
    }

    // ---------------------------------------------------------------- actions

    private void play(CharacterRegistry.Entry character) {
        // Saving here also creates the list on the first visit, fixing character 1 to the account's UUID.
        this.registry.save();
        CharacterClient.continueJoin(this.worldEntry, this.registry, character);
    }

    private void create() {
        if (!this.registry.canAdd()) return;
        String defaultName = this.defaultNewName();
        this.minecraft.setScreen(new CharacterNameScreen(Component.translatable("terrainlab.character.new.title"),
                Component.translatable("terrainlab.character.new.confirm"), this.registry, defaultName, null, null, false,
                () -> this.minecraft.setScreen(this), result -> {
            CharacterRegistry.Entry created = this.registry.add(result.name()).withSkin(result.skin(), result.slim());
            this.registry.replace(created);
            this.play(created);
        }));
    }

    private String defaultNewName() {
        int n = this.registry.characters().size() + 1;
        while (this.registry.isNameTaken("Character " + n, null)) n++;
        return "Character " + n;
    }

    private void rename(CharacterRegistry.Entry character) {
        this.minecraft.setScreen(new CharacterNameScreen(Component.translatable("terrainlab.character.rename.title"),
                CommonComponents.GUI_DONE, this.registry, character.name(), character.uuid(), character.skin(), character.slim(),
                () -> this.minecraft.setScreen(this), result -> {
            this.registry.replace(character.withName(result.name()).withSkin(result.skin(), result.slim()));
            this.registry.save();
            this.minecraft.setScreen(this);
        }));
    }

    private void confirmDelete(CharacterRegistry.Entry character) {
        if (this.registry.characters().size() <= 1) return;
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed && this.registry.characters().size() > 1) {
                CharacterFiles.delete(this.worldDir, character.uuid());
                this.registry.remove(character.uuid());
                this.registry.save();
            }
            this.minecraft.setScreen(this);
        },
                Component.translatable("terrainlab.character.delete.title", character.name()),
                Component.translatable("terrainlab.character.delete.message", character.name()),
                Component.translatable("terrainlab.character.delete.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    @Override
    public void onClose() {
        CharacterSession.clearPending();
        this.back.run();
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 8, WHITE);
        graphics.drawCenteredString(this.font, Component.literal(this.summary.getLevelName()), cx, 20, GREY);

        int textWidth = this.rowWidth - BUTTON_COLUMN - 14;
        for (Row row : this.rows) {
            boolean hovered = mouseX >= row.x() && mouseX < row.x() + this.rowWidth && mouseY >= row.y() && mouseY < row.y() + this.rowHeight;
            graphics.fill(row.x(), row.y(), row.x() + this.rowWidth, row.y() + this.rowHeight, hovered ? ROW_BG_HOVER : ROW_BG);
            if (hovered) {
                graphics.fill(row.x(), row.y(), row.x() + this.rowWidth, row.y() + 1, ROW_BORDER);
                graphics.fill(row.x(), row.y() + this.rowHeight - 1, row.x() + this.rowWidth, row.y() + this.rowHeight, ROW_BORDER);
            }
            int tx = row.x() + 5;
            int ty = row.y() + 3;
            String name = row.character().name();
            graphics.drawString(this.font, this.fit(name, textWidth), tx, ty, WHITE);
            if (row.info().dead()) {
                int nameWidth = this.font.width(this.fit(name, textWidth));
                Component dead = Component.translatable("terrainlab.character.dead");
                if (nameWidth + 6 + this.font.width(dead) <= textWidth) {
                    graphics.drawString(this.font, dead, tx + nameWidth + 6, ty, RED);
                }
            }
            String line2;
            String line3;
            CharacterFiles.Info info = row.info();
            if (info.hasData()) {
                line2 = Component.translatable("terrainlab.character.details",
                        dimensionName(info.dimension()), info.xpLevel(), formatHealth(info.health())).getString();
                long lastPlayed = row.character().lastPlayed() > 0 ? row.character().lastPlayed() : info.fileTime();
                line3 = Component.translatable("terrainlab.character.times",
                        formatPlayTime(info.playTicks()), DATE_FORMAT.format(Instant.ofEpochMilli(lastPlayed))).getString();
            } else {
                line2 = Component.translatable("terrainlab.character.fresh").getString();
                line3 = "";
            }
            graphics.drawString(this.font, this.fit(line2, textWidth), tx, ty + 12, GREY);
            if (!line3.isEmpty()) graphics.drawString(this.font, this.fit(line3, textWidth), tx, ty + 24, GREY);
        }
        super.render(graphics, mouseX, mouseY, a);
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("..."))) + "...";
    }

    private static String dimensionName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("terrainlab.character.dimension.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("terrainlab.character.dimension.nether").getString();
            case "minecraft:the_end" -> Component.translatable("terrainlab.character.dimension.end").getString();
            default -> {
                int colon = id.indexOf(':');
                String path = colon >= 0 ? id.substring(colon + 1) : id;
                path = path.replace('_', ' ');
                yield path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }
        };
    }

    private static String formatHealth(float health) {
        return health == Math.floor(health) ? Integer.toString((int) health) : String.format(Locale.ROOT, "%.1f", health);
    }

    private static String formatPlayTime(long ticks) {
        long minutes = ticks / 20L / 60L;
        long hours = minutes / 60L;
        if (hours > 0) {
            return Component.translatable("terrainlab.character.playtime.hours", hours, minutes % 60L).getString();
        }
        return Component.translatable("terrainlab.character.playtime.minutes", minutes).getString();
    }
}
