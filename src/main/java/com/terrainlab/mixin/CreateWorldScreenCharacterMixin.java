package com.terrainlab.mixin;

import com.terrainlab.client.character.CharacterClient;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Singleplayer characters: "Create New World" first asks for the world's first character (name and
 * skin). {@code onCreate} is the create screen's single path to world creation (the Create button
 * and Enter); this cancels it, shows the naming screen, and on confirm calls it again with the hook
 * let through, so vanilla's experimental-settings warning and creation flow run unchanged. The
 * choice is turned into the new world's character list when its integrated server starts
 * ({@code CharacterSession#claim}).
 */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenCharacterMixin {

    @Unique
    private static boolean terrainlab$continuing;

    @Shadow
    private void onCreate() {
    }

    @Inject(method = "onCreate", at = @At("HEAD"), cancellable = true)
    private void terrainlab$chooseFirstCharacter(CallbackInfo ci) {
        if (terrainlab$continuing) return;
        ci.cancel();
        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        CharacterClient.beginNewWorld(self, () -> {
            terrainlab$continuing = true;
            try {
                this.onCreate();
            } finally {
                terrainlab$continuing = false;
            }
        });
    }
}
