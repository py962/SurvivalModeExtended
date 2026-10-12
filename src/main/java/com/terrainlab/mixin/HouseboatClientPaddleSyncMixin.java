package com.terrainlab.mixin;

import com.terrainlab.entity.HouseboatEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only (references {@link Minecraft}; registered in the client mixin list). A houseboat suspended
 * from a Happy Ghast is not client-authoritative, so vanilla never sends its paddle packet; this resends
 * the A/D state at the end of {@code AbstractBoat#tick} so the server can steer the ghast.
 */
@Mixin(AbstractBoat.class)
public class HouseboatClientPaddleSyncMixin {

    @Shadow private boolean inputLeft;
    @Shadow private boolean inputRight;
    @Shadow private boolean inputUp;

    @Inject(method = "tick", at = @At("TAIL"))
    private void terrainlab$resendPaddleWhenSuspended(CallbackInfo ci) {
        AbstractBoat boat = (AbstractBoat) (Object) this;
        if (!boat.level().isClientSide()) return;
        if (!(boat instanceof HouseboatEntity houseboat) || !houseboat.isSuspendedFromGhast()) return;
        if (!boat.isVehicle()) return;

        // Paddle state is HappyGhastMixin's turn signal, so vanilla's "|| inputUp" term is omitted (W alone
        // would read as a turn). The left/right crossing matches vanilla.
        boolean left
 = this.inputRight && !this.inputLeft;
        boolean right = this.inputLeft && !this.inputRight;

        boat.setPaddleState(left, right);

        if (Minecraft.getInstance().getConnection() != null) {
            Minecraft.getInstance().getConnection().send(new ServerboundPaddleBoatPacket(left, right));
        }
    }
}
