package com.terrainlab.client.character;

import com.terrainlab.character.CharacterRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Asks for a character name (new character or rename): one text box, capped at
 * {@link CharacterRegistry#MAX_NAME_LENGTH} characters, that must not be blank or match another
 * character of the same world.
 */
public class CharacterNameScreen extends Screen {

    private static final int RED = 0xFFFF5555;
    private static final int GREY = 0xFFA0A0A0;

    private final Screen parent;
    private final CharacterRegistry registry;
    private final String initialName;
    private final UUID renaming;
    private final Component confirmLabel;
    private final Consumer<String> onConfirm;

    private EditBox nameBox;
    private Button confirmButton;
    private Component error = null;

    /**
     * @param renaming the character being renamed (its own name is not a clash), or null for a new one
     */
    public CharacterNameScreen(Screen parent, Component title, Component confirmLabel, CharacterRegistry registry,
                               String initialName, UUID renaming, Consumer<String> onConfirm) {
        super(title);
        this.parent = parent;
        this.registry = registry;
        this.initialName = initialName;
        this.renaming = renaming;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        String value = this.nameBox != null ? this.nameBox.getValue() : this.initialName;
        this.nameBox = new EditBox(this.font, cx - 100, cy - 20, 200, 20, Component.translatable("terrainlab.character.name"));
        this.nameBox.setMaxLength(CharacterRegistry.MAX_NAME_LENGTH);
        this.nameBox.setValue(value);
        this.nameBox.setResponder(text -> this.validate());
        this.addRenderableWidget(this.nameBox);

        this.confirmButton = this.addRenderableWidget(Button.builder(this.confirmLabel, b -> this.confirm())
                .bounds(cx - 102, cy + 18, 100, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                .bounds(cx + 2, cy + 18, 100, 20).build());
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
        } else if (this.registry.isNameTaken(name, this.renaming)) {
            this.error = Component.translatable("terrainlab.character.name_taken");
            this.confirmButton.active = false;
        } else {
            this.error = null;
            this.confirmButton.active = true;
        }
    }

    private void confirm() {
        if (!this.confirmButton.active) return;
        this.onConfirm.accept(CharacterRegistry.sanitizeName(this.nameBox.getValue(), this.initialName));
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
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.centeredText(this.font, this.title, cx, cy - 60, 0xFFFFFFFF);
        graphics.text(this.font, Component.translatable("terrainlab.character.name"), cx - 100, cy - 32, GREY);
        if (this.error != null) {
            graphics.centeredText(this.font, this.error, cx, cy + 4, RED);
        }
    }
}
