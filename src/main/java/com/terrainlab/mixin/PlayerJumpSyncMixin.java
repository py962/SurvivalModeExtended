package com.terrainlab.mixin;

import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies jump, forward/back and shift state from every player input packet onto
 * the server player, since vanilla does not keep them current for a seated passenger.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class PlayerJumpSyncMixin {

    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void terrainlab$syncJumpWhileRiding(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = self.player;
        var input = packet.input();

        ((LivingEntityAccessor) player).setJumping(input.jump());
        player.zza = input.forward() ? 1.0f : (input.backward() ? -1.0f : 0.0f);
        player.setShiftKeyDown(input.shift());
        com.terrainlab.util.ShiftStateCache.shiftHeld.put(player.getUUID(), input.shift());
    }
}
