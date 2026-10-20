package com.terrainlab.mixin;

import com.terrainlab.food.FlowerStews;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes FlowerBlock.getSuspiciousEffects return the effects of the mod's crafted flower stews
 * (FlowerStews, mirroring the suspicious_stew_from_&lt;flower&gt; recipes), so anything outside crafting
 * that asks a flower for its stew effects (e.g. brown mooshrooms) agrees with the recipes: Dandelion gives
 * Resistance, Blue Orchid and Oxeye Daisy give no MobEffect (their energy / instant heal is applied by
 * ModFoodEffectsHandler through the stew_flower component), other durations match the recipes.
 */
@Mixin(FlowerBlock.class)
public abstract class FlowerStewEffectMixin {

    @Inject(method = "getSuspiciousEffects", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$craftedStewEffects(CallbackInfoReturnable<SuspiciousStewEffects> cir) {
        FlowerStews.Stew stew = FlowerStews.forItem(((Block) (Object) this).asItem());
        if (stew != null) {
            cir.setReturnValue(stew.effects());
        }
    }
}
