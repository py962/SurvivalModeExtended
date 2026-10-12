package com.terrainlab.mixin;

import com.terrainlab.enchantment.ServerChestplateReach;
import net.minecraft.core.BlockPos;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Server-side counterpart of ChestplateReachClientMixin: widens the range check for block placement packets. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChestplateReachServerMixin {

    @Redirect(
            method = "handleUseItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;isWithinBlockInteractionRange(Lnet/minecraft/core/BlockPos;D)Z"
            )
    )
    private boolean terrainlab$allowChestplatePlacementReach(
            ServerPlayer player,
            BlockPos pos,
            double range
    ) {
        ItemStack stack = player.getItemInHand(player.getUsedItemHand());

        if (!(stack.getItem() instanceof BlockItem)) {
            return player.isWithinBlockInteractionRange(pos, range);
        }

        return ServerChestplateReach.isWithinPlacementRange(
                player,
                pos,
                range
        );
    }
}
