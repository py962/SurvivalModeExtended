package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrainlab.village.VillageBeds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Breeding food is spent on a successful birth only; vanilla spends it before checking for a free bed.
 * No births while more than a couple of villagers in the village have no bed.
 */
@Mixin(VillagerMakeLove.class)
public abstract class VillagerBreedFoodOnBirthMixin {

    @WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/villager/Villager;eatAndDigestFood()V"),
            require = 2)
    private void terrainlab$noFoodBeforeBirth(Villager villager, Operation<Void> original) {
    }

    @Inject(method = "tryToGiveBirth", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$houseBedlessFirst(ServerLevel level, Villager body, Villager target, CallbackInfo ci) {
        if (VillageBeds.bedlessNeighbours(level, body) > VillageBeds.BEDLESS_MARGIN) {
            // Same angry particles as vanilla's no-bed failure.
            level.broadcastEntityEvent(target, (byte) 13);
            level.broadcastEntityEvent(body, (byte) 13);
            ci.cancel();
        }
    }

    @Inject(method = "tryToGiveBirth",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/VillagerMakeLove;giveBedToChild(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/core/BlockPos;)V"),
            require = 1)
    private void terrainlab$foodOnBirth(ServerLevel level, Villager body, Villager target, CallbackInfo ci) {
        body.eatAndDigestFood();
        target.eatAndDigestFood();
    }
}
