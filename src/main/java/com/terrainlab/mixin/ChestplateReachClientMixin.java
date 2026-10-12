package com.terrainlab.mixin;

import com.terrainlab.enchantment.ClientChestplateReach;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Extends the client pick range for block placement when a block item is held and chestplate reach applies. */
@Mixin(LocalPlayer.class)
public abstract class ChestplateReachClientMixin {

    @Redirect(
            method = "raycastHitResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;blockInteractionRange()D"
            )
    )
    private double terrainlab$allowChestplatePlacementReach(
            LocalPlayer player
    ) {
        ItemStack stack = player.getActiveItem();

        if (!(stack.getItem() instanceof BlockItem)) {
            return player.blockInteractionRange();
        }

        return ClientChestplateReach.getPlacementRange(
                player,
                player.blockInteractionRange()
        );
    }
}
