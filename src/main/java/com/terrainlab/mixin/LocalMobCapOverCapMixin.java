package com.terrainlab.mixin;

import com.terrainlab.difficulty.OverCapDifficulty;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/** Past the difficulty cap, the hostile mob cap around a player rises by up to 50% (see OverCapDifficulty). */
@Mixin(LocalMobCapCalculator.class)
public abstract class LocalMobCapOverCapMixin {

    @Shadow @Final private Map<ServerPlayer, ?> playerMobCounts;

    @Shadow
    private List<ServerPlayer> getPlayersNear(ChunkPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "canSpawn", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$overCapMonsters(MobCategory category, ChunkPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (category != MobCategory.MONSTER || OverCapDifficulty.highestExcess() <= 0.0F) return;
        for (ServerPlayer player : this.getPlayersNear(pos)) {
            Object counts = this.playerMobCounts.get(player);
            if (counts == null) {
                cir.setReturnValue(true);
                return;
            }
            float cap = category.getMaxInstancesPerChunk()
                    * (1.0F + OverCapDifficulty.MOB_CAP_BONUS * OverCapDifficulty.playerExcess(player));
            if (((MobCountsAccessor) counts).terrainlab$counts().getInt(category) < cap) {
                cir.setReturnValue(true);
                return;
            }
        }
        cir.setReturnValue(false);
    }
}
