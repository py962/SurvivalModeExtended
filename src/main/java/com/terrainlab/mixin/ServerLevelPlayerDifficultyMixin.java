package com.terrainlab.mixin;

import com.terrainlab.difficulty.PlayerDifficulty;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local difficulty comes from the nearest player's play time and hostile kills (see PlayerDifficulty). */
@Mixin(ServerLevel.class)
public abstract class ServerLevelPlayerDifficultyMixin {

    @Inject(method = "getCurrentDifficultyAt", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$playerDifficulty(BlockPos pos, CallbackInfoReturnable<DifficultyInstance> cir) {
        float effective = PlayerDifficulty.effectiveAt((ServerLevel) (Object) this, pos);
        if (!Float.isNaN(effective)) {
            DifficultyInstance instance = cir.getReturnValue();
            ((DifficultyInstanceAccessor) instance).terrainlab$setEffectiveDifficulty(effective);
        }
    }
}
