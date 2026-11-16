package com.terrainlab.mixin;

import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Level thresholds matched to the trade XP (1 / 2 / 5 / 10 per trade): 5, 4, 3 and 2 trades from
 * Novice to Master. Also drives the progress bar on the trade screen.
 */
@Mixin(VillagerData.class)
public abstract class VillagerDataThresholdMixin {

    @Shadow
    @Final
    private static int[] NEXT_LEVEL_XP_THRESHOLDS;

    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void terrainlab$tradeXpThresholds(CallbackInfo ci) {
        int[] thresholds = {0, 5, 13, 28, 40};
        System.arraycopy(thresholds, 0, NEXT_LEVEL_XP_THRESHOLDS, 0, thresholds.length);
    }
}
