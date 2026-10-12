package com.terrainlab.mixin;

import com.terrainlab.registry.ModMobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps players with Phasing free of block collision. {@code Player#tick} resets
 * {@code noPhysics = isSpectator()} every tick; this sets it back to true right after that write.
 * Gravity and suffocation are handled by PhasingEffectHandler.
 */
@Mixin(Player.class)
public abstract class PhasingNoPhysicsMixin {

    @Inject(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Player;noPhysics:Z",
                    opcode = org.objectweb.asm.Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void terrainlab$forcePhasingNoPhysics(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.hasEffect(ModMobEffects.PHASING)) {
            self.noPhysics = true;
        }
    }
}
