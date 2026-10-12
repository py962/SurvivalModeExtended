package com.terrainlab.mixin;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Removes Saturation from flower stew effects outside crafting, matching the mod's remapped recipes
 * (the hunger system makes Saturation a free hunger refill). Used by brown mooshrooms fed a flower:
 * Dandelion now gives Resistance (8 s, as its crafted stew), Blue Orchid gives no effect (its crafted
 * Energy stew is handled by ModFoodEffectsHandler through the stew_flower component, which found
 * stews do not carry).
 */
@Mixin(FlowerBlock.class)
public abstract class FlowerStewEffectMixin {

    @Unique
    private static final SuspiciousStewEffects TERRAINLAB$DANDELION = new SuspiciousStewEffects(
            List.of(new SuspiciousStewEffects.Entry(MobEffects.RESISTANCE, SuspiciousStewEffects.DEFAULT_DURATION)));

    @Inject(method = "getSuspiciousEffects", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$remapSaturation(CallbackInfoReturnable<SuspiciousStewEffects> cir) {
        Object self = this;
        if (self == Blocks.DANDELION) {
            cir.setReturnValue(TERRAINLAB$DANDELION);
        } else if (self == Blocks.BLUE_ORCHID) {
            cir.setReturnValue(SuspiciousStewEffects.EMPTY);
        }
    }
}
