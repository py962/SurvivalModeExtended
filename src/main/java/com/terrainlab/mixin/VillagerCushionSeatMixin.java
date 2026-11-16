package com.terrainlab.mixin;

import com.terrainlab.hearth.HearthCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Villagers have no riding offset, so on a cushion they would sit a hip's height too high. */
@Mixin(Entity.class)
public abstract class VillagerCushionSeatMixin {

    // Same drop players and illagers get from ridingOffset(-0.6).
    @Unique
    private static final double TERRAINLAB$SEAT_DROP = 0.6;

    @Inject(method = "getVehicleAttachmentPoint", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$lowerOnCushion(Entity vehicle, CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this instanceof Villager && HearthCompat.isCushion(vehicle)) {
            cir.setReturnValue(cir.getReturnValue().add(0.0, TERRAINLAB$SEAT_DROP, 0.0));
        }
    }
}
