package com.terrainlab.mixin;

import com.terrainlab.village.work.FishermanRod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.ShowTradesToPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A fishing fisherman keeps its rod: vanilla's trade display neither starts for it (so it keeps watching
 * the water) nor touches its main hand (a display already running when fishing began stops on its next
 * check, and its clear on stop is skipped too).
 */
@Mixin(ShowTradesToPlayer.class)
public abstract class ShowTradesToPlayerFishingMixin {

    @Inject(
            method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void terrainlab$notWhileFishing(ServerLevel level, Villager body, CallbackInfoReturnable<Boolean> cir) {
        if (FishermanRod.isFishing(body)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "clearHeldItem", at = @At("HEAD"), cancellable = true)
    private static void terrainlab$keepRodOnClear(Villager body, CallbackInfo ci) {
        if (FishermanRod.isFishing(body)) {
            ci.cancel();
        }
    }

    @Inject(method = "displayAsHeldItem", at = @At("HEAD"), cancellable = true)
    private static void terrainlab$keepRodOnDisplay(Villager body, ItemStack itemStack, CallbackInfo ci) {
        if (FishermanRod.isFishing(body)) {
            ci.cancel();
        }
    }
}
