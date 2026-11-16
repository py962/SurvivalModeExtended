package com.terrainlab.mixin;

import com.terrainlab.item.MapZoomCrafter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exposes the crafting player to post-processing, which vanilla only gives the level. */
@Mixin(Item.class)
public abstract class ItemCraftedByPlayerMixin {

    @Inject(method = "onCraftedBy", at = @At("HEAD"), require = 1)
    private void terrainlab$rememberCrafter(ItemStack stack, Player player, CallbackInfo ci) {
        MapZoomCrafter.set(player);
    }

    @Inject(method = "onCraftedBy", at = @At("RETURN"), require = 1)
    private void terrainlab$forgetCrafter(ItemStack stack, Player player, CallbackInfo ci) {
        MapZoomCrafter.set(null);
    }
}
