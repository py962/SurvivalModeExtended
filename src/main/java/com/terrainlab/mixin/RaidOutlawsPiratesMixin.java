package com.terrainlab.mixin;

import com.terrainlab.raid.RaidOutlawsPirates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After each vanilla raid wave spawns, adds that wave's outlaws on horses and pirates in boats. */
@Mixin(Raid.class)
public abstract class RaidOutlawsPiratesMixin {

    @Inject(method = "spawnGroup", at = @At("TAIL"), require = 1)
    private void terrainlab$addOutlawsAndPirates(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        RaidOutlawsPirates.spawnExtras((Raid) (Object) this, level, pos);
    }
}
