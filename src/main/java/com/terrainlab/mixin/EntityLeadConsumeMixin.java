package com.terrainlab.mixin;

import com.terrainlab.event.EnchantedLeadHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla right-click leashing (any Leashable, incl. happy ghasts and boats): just before the held lead
 * is consumed, remember it on the newly leashed entity (see {@link EnchantedLeadHandler}).
 */
@Mixin(Entity.class)
public abstract class EntityLeadConsumeMixin {

    @Inject(
            method = "interact(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void terrainlab$recordLead(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        EnchantedLeadHandler.record((Entity) (Object) this, player.getItemInHand(hand));
    }
}
