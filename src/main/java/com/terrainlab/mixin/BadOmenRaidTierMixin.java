package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrainlab.village.VillageRaids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Bad Omen only turns into Raid Omen inside a village of tier 2 or above. In a tier 1 village (or one
 * not yet known to the mod) nothing happens and the player keeps Bad Omen.
 */
@Mixin(targets = "net.minecraft.world.effect.BadOmenMobEffect")
public abstract class BadOmenRaidTierMixin {

    @WrapOperation(method = "applyEffectTick", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isVillage(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean terrainlab$raidableVillage(ServerLevel level, BlockPos pos, Operation<Boolean> original) {
        return original.call(level, pos) && VillageRaids.canBeRaided(level, pos);
    }
}
