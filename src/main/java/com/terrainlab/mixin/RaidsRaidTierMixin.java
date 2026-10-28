package com.terrainlab.mixin;

import com.terrainlab.village.VillageRaids;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Backstop for the Bad Omen check: no raid can start outside a village of tier 2 or above. */
@Mixin(Raids.class)
public abstract class RaidsRaidTierMixin {

    @Inject(method = "createOrExtendRaid", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$tierGate(ServerPlayer player, BlockPos pos, CallbackInfoReturnable<Raid> cir) {
        if (!VillageRaids.canBeRaided(player.level(), pos)) {
            cir.setReturnValue(null);
        }
    }
}
