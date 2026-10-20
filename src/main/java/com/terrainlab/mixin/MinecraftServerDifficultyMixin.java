package com.terrainlab.mixin;

import com.terrainlab.advancement.ChallengeEligibility;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-checks challenge eligibility as soon as the world difficulty changes (/difficulty, the pause
 * menu button), since NeoForge's DifficultyChangeEvent only fires on the client.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerDifficultyMixin {

    @Inject(method = "setDifficulty", at = @At("TAIL"))
    private void terrainlab$recheckChallenges(Difficulty difficulty, boolean ignoreLock, CallbackInfo ci) {
        ChallengeEligibility.updateAll((MinecraftServer) (Object) this);
    }
}
