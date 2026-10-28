package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.terrainlab.difficulty.OverCapDifficulty;
import net.minecraft.world.entity.MobCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Raises the world-wide hostile cap to match the highest per-player bonus (see LocalMobCapOverCapMixin). */
@Mixin(targets = "net.minecraft.world.level.NaturalSpawner$SpawnState")
public abstract class SpawnStateGlobalCapMixin {

    @ModifyExpressionValue(method = "canSpawnForCategoryGlobal", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/MobCategory;getMaxInstancesPerChunk()I"))
    private int terrainlab$overCapGlobal(int max, MobCategory category) {
        if (category != MobCategory.MONSTER) return max;
        return Math.round(max * (1.0F + OverCapDifficulty.MOB_CAP_BONUS * OverCapDifficulty.highestExcess()));
    }
}
