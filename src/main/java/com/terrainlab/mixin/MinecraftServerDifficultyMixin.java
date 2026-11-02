package com.terrainlab.mixin;

import com.terrainlab.advancement.ChallengeEligibility;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hardcore worlds can be Normal or Isolation (Hard): vanilla forces Hard on every hardcore
 * setDifficulty; here a hardcore world keeps Normal or Isolation and anything easier becomes Normal.
 * <p>
 * Re-checks challenge eligibility as soon as the world difficulty changes (/difficulty, the pause
 * menu button), since NeoForge's DifficultyChangeEvent only fires on the client.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerDifficultyMixin {

    @Shadow @Final protected WorldData worldData;

    @ModifyVariable(method = "setDifficulty", at = @At("HEAD"), argsOnly = true, require = 1)
    private Difficulty terrainlab$hardcoreNormalOrIsolation(Difficulty difficulty) {
        return this.worldData.isHardcore() && difficulty != Difficulty.HARD ? Difficulty.NORMAL : difficulty;
    }

    @Redirect(method = "setDifficulty", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/WorldData;isHardcore()Z"), require = 1)
    private boolean terrainlab$noForcedHard(WorldData data) {
        return false;
    }

    @Inject(method = "setDifficulty", at = @At("TAIL"))
    private void terrainlab$recheckChallenges(Difficulty difficulty, boolean ignoreLock, CallbackInfo ci) {
        ChallengeEligibility.updateAll((MinecraftServer) (Object) this);
    }
}
