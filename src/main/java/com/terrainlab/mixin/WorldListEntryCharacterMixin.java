package com.terrainlab.mixin;

import com.terrainlab.client.character.CharacterClient;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Singleplayer characters: opening an existing world from the world list shows the character
 * screen first.
 * <p>
 * {@code WorldSelectionList.WorldListEntry#joinWorld} is the single path behind the world list's
 * "Play Selected World" button, double-clicking an entry and pressing Enter on it
 * ({@code SelectWorldScreen} passes it as both the footer action and {@code onEntryInteract}).
 * When the world can be opened (same checks as vanilla: primary action active, not a symlinked
 * world, which vanilla routes to its warning screen), this cancels the call and opens the character
 * screen; the screen continues by calling {@code joinWorld} again inside
 * {@link CharacterClient#continueJoin}, which lets this call through so the vanilla flow (backup,
 * upgrade and experimental-feature prompts) runs unchanged. "Back" returns via the list's own
 * {@code returnToScreen}, exactly as cancelling a vanilla open does.
 * <p>
 * Brand-new worlds (create screen), the demo world and quick play do not go through here.
 */
@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryCharacterMixin {

    @Shadow @Final private WorldSelectionList list;

    @Shadow
    public abstract LevelSummary getLevelSummary();

    @Inject(method = "joinWorld", at = @At("HEAD"), cancellable = true)
    private void terrainlab$chooseCharacter(CallbackInfo ci) {
        LevelSummary summary = this.getLevelSummary();
        if (CharacterClient.isContinuingJoin()
                || !summary.primaryActionActive()
                || summary instanceof LevelSummary.SymlinkLevelSummary) {
            return;
        }
        WorldSelectionList.WorldListEntry self = (WorldSelectionList.WorldListEntry) (Object) this;
        WorldSelectionList worldList = this.list;
        if (CharacterClient.openCharacterScreen(self, summary, worldList::returnToScreen)) {
            ci.cancel();
        }
    }
}
