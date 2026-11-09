package com.terrainlab.mixin;

import com.terrainlab.village.VillagerHungerDebug;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Debug only: logs vanilla's breeding food use (see VillagerHungerDebug). */
@Mixin(Villager.class)
public abstract class VillagerDigestLogMixin {

    @Inject(method = "eatAndDigestFood", at = @At("HEAD"))
    private void terrainlab$logDigest(CallbackInfo ci) {
        VillagerHungerDebug.digest((Villager) (Object) this);
    }
}
