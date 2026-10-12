package com.terrainlab.mixin;

import com.terrainlab.food.FoodCategory;
import com.terrainlab.food.FoodCategoryTable;
import com.terrainlab.temperature.FoodTemperatureEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Debug aid: shortens {@link Item#getUseDuration(ItemStack, LivingEntity)} for food and drinks
 * tracked by the food/temperature systems, so eating interruptions can be distinguished from
 * releasing too early. Gated by {@link #DEBUG_SHORTEN_ENABLED}; changes gameplay balance when on.
 */
@Mixin(Item.class)
public abstract class DebugShortenFoodUseDurationMixin {

    private static final boolean DEBUG_SHORTEN_ENABLED = false;

    // ticks
    private static final int SHORTENED_TICKS = 6;

    @Inject(method = "getUseDuration", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$shortenTrackedFoodDuration(
            ItemStack stack,
            LivingEntity entity,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (!DEBUG_SHORTEN_ENABLED) {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        FoodCategory category = FoodCategoryTable.getCategory(item);
        boolean tracked = category != null || FoodTemperatureEffects.isWaterBottle(stack);
        if (!tracked) {
            return;
        }
        cir.setReturnValue(SHORTENED_TICKS);
    }
}
