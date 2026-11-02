package com.terrainlab.mixin;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create World: Hardcore offers Normal (the default) or Isolation instead of forcing Hard. The
 * hardcore choice is kept apart from the ordinary difficulty, so switching game mode back and forth
 * restores each; choosing Peaceful or Easy while hardcore (the button cycles all four) gives Normal,
 * so the button toggles between Normal and Isolation.
 */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateHardcoreMixin {

    @Unique
    private Difficulty terrainlab$hardcoreDifficulty = Difficulty.NORMAL;

    @Shadow
    public abstract boolean isHardcore();

    @Shadow
    public abstract void onChanged();

    @Inject(method = "getDifficulty", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$hardcoreDifficulty(CallbackInfoReturnable<Difficulty> cir) {
        if (this.isHardcore()) {
            cir.setReturnValue(this.terrainlab$hardcoreDifficulty);
        }
    }

    @Inject(method = "setDifficulty", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$setHardcoreDifficulty(Difficulty difficulty, CallbackInfo ci) {
        if (this.isHardcore()) {
            this.terrainlab$hardcoreDifficulty = difficulty == Difficulty.HARD ? Difficulty.HARD : Difficulty.NORMAL;
            this.onChanged();
            ci.cancel();
        }
    }
}
