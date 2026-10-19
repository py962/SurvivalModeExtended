package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrainlab.client.combat.OffhandStrikeInputHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dual wielding: the off-hand strike replaces vanilla's {@code useItem(OFF_HAND)} call in
 * startUseItem, the exact point where an off-hand shield would be raised (everything the main
 * hand, and the off hand's own entity/block interaction, could do has already been tried).
 * Returning PASS ends the click: the off hand is the last hand in the loop.
 */
@Mixin(Minecraft.class)
public abstract class OffhandStrikeUseItemMixin {

    @WrapOperation(
            method = "startUseItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult terrainlab$offhandStrike(MultiPlayerGameMode gameMode, Player player, InteractionHand hand,
                                                       Operation<InteractionResult> original) {
        if (hand == InteractionHand.OFF_HAND
                && OffhandStrikeInputHandler.tryReplaceOffhandUse((Minecraft) (Object) this)) {
            return InteractionResult.PASS;
        }
        return original.call(gameMode, player, hand);
    }
}
