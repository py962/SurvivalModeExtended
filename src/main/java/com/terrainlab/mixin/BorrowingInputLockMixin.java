package com.terrainlab.mixin;

import com.terrainlab.api.BorrowingAccess;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops gameplay input packets on the server while the player is borrowing, so the player stays a
 * passive observer: movement, player commands, player actions, item use, entity interaction, attacks
 * and hotbar selection. Leaving bed, chat and client settings are not blocked, so the
 * player can always end a borrow by getting up.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class BorrowingInputLockMixin {

    @Unique
    private boolean terrainlab$borrowingActive() {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = self.player;
        return player != null && ((BorrowingAccess) player).terrainlab$isBorrowing();
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (terrainlab$borrowingActive()) ci.cancel();
    }

    @Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockPlayerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerCommandPacket.Action.STOP_SLEEPING) return;
        if (terrainlab$borrowingActive()) ci.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (terrainlab$borrowingActive()) ci.cancel();
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        if (terrainlab$borrowingActive()) ci.cancel();
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        if (terrainlab$borrowingActive()) ci.cancel();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        if (terrainlab$borrowingActive()) ci.cancel();
    }

    // 26.x split attacks out of ServerboundInteractPacket; block them too to keep the old behaviour.
    // (The serverbound swing packet / handleAnimate no longer exists.)
    @Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
        if (terrainlab$borrowingActive()) ci.cancel();
    }

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void terrainlab$blockSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (terrainlab$borrowingActive()) ci.cancel();
    }
}
