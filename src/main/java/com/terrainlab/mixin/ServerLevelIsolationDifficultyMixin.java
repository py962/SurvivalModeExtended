package com.terrainlab.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Isolation (Hard) plays exactly like Normal; only its village generation differs (HardcoreVillageRules,
 * which reads the world's stored difficulty, not this). Every server-side gameplay check reads the
 * difficulty through {@code getDifficulty()}, so a Hard world reports Normal here: mob damage,
 * reinforcements, raids, effect durations and the per-player difficulty scale all follow Normal.
 * The stored difficulty, the difficulty button and the client are untouched, so the world still
 * shows as Isolation.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelIsolationDifficultyMixin {

    public Difficulty getDifficulty() {
        Difficulty stored = ((ServerLevel) (Object) this).getLevelData().getDifficulty();
        return stored == Difficulty.HARD ? Difficulty.NORMAL : stored;
    }
}
