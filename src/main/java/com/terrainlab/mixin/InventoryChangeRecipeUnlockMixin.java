package com.terrainlab.mixin;

import com.terrainlab.recipe.RecipeUnlockService;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks {@code InventoryChangeTrigger#trigger}, which vanilla fires on every
 * player-inventory slot change, to unlock recipes from any obtained ingredient.
 */
@Mixin(InventoryChangeTrigger.class)
public abstract class InventoryChangeRecipeUnlockMixin {

    @Inject(
            method = "trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"),
            require = 1
    )
    private void terrainlab$unlockRecipesFromAnyIngredient(ServerPlayer player, Inventory inventory, ItemStack stack, CallbackInfo ci) {
        RecipeUnlockService.onItemObtained(player, stack);
    }
}
