package com.terrainlab.mixin;

import com.terrainlab.registry.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Stops a dragon with Phase Blocking from destroying blocks in checkWalls. */
@Mixin(EnderDragon.class)
public abstract class EnderDragonPhaseBlockingMixin {

    @Redirect(method = "checkWalls", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/CommonHooks;canEntityDestroy(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean terrainlab$suppressDragonDestroy(ServerLevel level, BlockPos pos, LivingEntity entity) {
        if (entity instanceof EnderDragon dragon && dragon.hasEffect(ModMobEffects.PHASE_BLOCKING)) {
            return false;
        }
        return CommonHooks.canEntityDestroy(level, pos, entity);
    }
}
