package com.terrainlab.mixin;

import com.terrainlab.registry.ModMobEffects;
import net.minecraft.world.entity.monster.Vex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops a Vex under Phase Blocking from passing through blocks. */
@Mixin(Vex.class)
public abstract class VexPhaseBlockingMixin {

    // Vex#tick sets noPhysics = true before super.tick(); reset it right after that write.
    @Inject(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/monster/Vex;noPhysics:Z",
                    opcode = org.objectweb.asm.Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER,
                    ordinal = 0
            )
    )
    private void terrainlab$suppressVexNoPhysics(CallbackInfo ci) {
        Vex self = (Vex) (Object) this;
        if (self.hasEffect(ModMobEffects.PHASE_BLOCKING)) {
            self.noPhysics = false;
        }
    }
}
