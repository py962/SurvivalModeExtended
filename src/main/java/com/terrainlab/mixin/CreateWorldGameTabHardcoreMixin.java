package com.terrainlab.mixin;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Create World, Game tab: vanilla greys out the difficulty button in Hardcore. It stays usable here
 * (Normal or Isolation, see WorldCreationUiStateHardcoreMixin): this listener is added after
 * vanilla's, so it runs last on every change.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$GameTab")
public abstract class CreateWorldGameTabHardcoreMixin {

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void terrainlab$keepDifficultyButtonActive(CreateWorldScreen screen, CallbackInfo ci) {
        WorldCreationUiState state = screen.getUiState();
        ((GridLayoutTab) (Object) this).visitChildren(widget -> {
            if (widget instanceof CycleButton<?> button && button.getValue() instanceof Difficulty) {
                button.active = true;
                state.addListener(changed -> button.active = true);
            }
        });
    }
}
