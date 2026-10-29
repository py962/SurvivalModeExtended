package com.terrainlab.client.character;

import com.terrainlab.character.CharacterRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Names a character and picks its skin (new character, editing one, or the first character of a
 * world): a text box capped at {@link CharacterRegistry#MAX_NAME_LENGTH} characters that must not
 * be blank or match another character of the same world, one of vanilla's nine default skins with
 * wide or slim arms, and a rotating preview (drag to turn it).
 */
public class CharacterNameScreen extends Screen {

    private static final int RED = 0xFFFF5555;
    private static final int GREY = 0xFFA0A0A0;

    /** What the player chose. */
    public record Result(String name, String skin, boolean slim) {
    }

    private final CharacterRegistry registry;
    private final String initialName;
    private final UUID editing;
    private final Component confirmLabel;
    private final Runnable onCancel;
    private final Consumer<Result> onConfirm;

    private String skin;
    private boolean slim;
    private EditBox nameBox;
    private Button confirmButton;
    private Component error = null;

    /**
     * @param registry    the world's characters, for the name clash check; null when the world does not exist yet
     * @param editing     the character being edited (its own name is not a clash), or null for a new one
     * @param initialSkin the skin to start on, or null to start on a random one
     */
    public CharacterNameScreen(Component title, Component confirmLabel, CharacterRegistry registry, String initialName,
                               UUID editing, String initialSkin, boolean initialSlim, Runnable onCancel, Consumer<Result> onConfirm) {
        super(title);
        this.registry = registry;
        this.initialName = initialName;
        this.editing = editing;
        this.confirmLabel = confirmLabel;
        this.onCancel = onCancel;
        this.onConfirm = onConfirm;
        if (CharacterSkins.isValid(initialSkin)) {
            this.skin = initialSkin;
            this.slim = initialSlim;
        } else {
            RandomSource random = RandomSource.create();
            this.skin = CharacterSkins.randomName(random);
            this.slim = random.nextBoolean();
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - 50;

        PlayerSkinWidget preview = new PlayerSkinWidget(80, 120, this.minecraft.getEntityModels(), () -> CharacterSkins.skin(this.skin, this.slim));
        preview.setPosition(cx - 150, cy - 64);
        this.addRenderableWidget(preview);

        String value = this.nameBox != null ? this.nameBox.getValue() : this.initialName;
        this.nameBox = new EditBox(this.font, left, cy - 52, 200, 20, Component.translatable("terrainlab.character.name"));
        this.nameBox.setMaxLength(CharacterRegistry.MAX_NAME_LENGTH);
        this.nameBox.setValue(value);
        this.nameBox.setResponder(text -> this.validate());
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(CycleButton.builder((String name) -> Component.translatable("terrainlab.character.skin." + name), this.skin)
                .withValues(CharacterSkins.NAMES)
                .create(left, cy - 24, 200, 20, Component.translatable("terrainlab.character.skin"), (button, name) -> this.skin = name));
        this.addRenderableWidget(CycleButton.builder((Boolean isSlim) -> Component.translatable(isSlim ? "terrainlab.character.arms.slim" : "terrainlab.character.arms.wide"), this.slim)
                .withValues(List.of(false, true))
                .create(left, cy, 200, 20, Component.translatable("terrainlab.character.arms"), (button, isSlim) -> this.slim = isSlim));

        this.confirmButton = this.addRenderableWidget(Button.builder(this.confirmLabel, b -> this.confirm())
                .bounds(left, cy + 40, 98, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                .bounds(left + 102, cy + 40, 98, 20).build());
        this.validate();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.nameBox);
    }

    private void validate() {
        String name = this.nameBox.getValue().trim();
        if (name.isEmpty()) {
            this.error = null;
            this.confirmButton.active = false;
        } else if (this.registry != null && this.registry.isNameTaken(name, this.editing)) {
            this.error = Component.translatable("terrainlab.character.name_taken");
            this.confirmButton.active = false;
        } else {
            this.error = null;
            this.confirmButton.active = true;
        }
    }

    private void confirm() {
        if (!this.confirmButton.active) return;
        this.onConfirm.accept(new Result(CharacterRegistry.sanitizeName(this.nameBox.getValue(), this.initialName), this.skin, this.slim));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && this.confirmButton.active) {
            this.confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.onCancel.run();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        super.render(graphics, mouseX, mouseY, a);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, this.title, cx, cy - 84, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.translatable("terrainlab.character.name"), cx - 50, cy - 64, GREY);
        if (this.error != null) {
            graphics.drawCenteredString(this.font, this.error, cx + 50, cy + 26, RED);
        }
    }
}
