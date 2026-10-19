package com.terrainlab.mixin;

import com.terrainlab.event.EnchantedLeadHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leash drops use the lead that formed the leash (see {@link EnchantedLeadHandler}): both places vanilla
 * spawns {@code Items.LEAD} for a broken leash spawn the stored lead instead.
 */
@Mixin(Leashable.class)
public interface LeashableEnchantedLeadMixin {

    /** Untie/break/shear: drop the stored lead instead of a plain one. */
    @Redirect(
            method = "dropLeash(Lnet/minecraft/world/entity/Entity;ZZ)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private static ItemEntity terrainlab$dropStoredLead(Entity entity, ServerLevel level, ItemLike item) {
        return entity.spawnAtLocation(level, EnchantedLeadHandler.takeLeadToDrop(entity));
    }

    /** Saved leash whose holder never reappeared: same. */
    @Redirect(
            method = "restoreLeashFromSave(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Leashable$LeashData;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private static ItemEntity terrainlab$dropStoredLeadOnRestore(Entity entity, ServerLevel level, ItemLike item) {
        return entity.spawnAtLocation(level, EnchantedLeadHandler.takeLeadToDrop(entity));
    }

    /** Leash removed without a drop (creative untie, dimension change, ...): the lead is gone, forget it. */
    @Inject(method = "dropLeash(Lnet/minecraft/world/entity/Entity;ZZ)V", at = @At("HEAD"))
    private static void terrainlab$forgetOnRemove(Entity entity, boolean sendPacket, boolean dropLead, CallbackInfo ci) {
        if (dropLead || entity.level().isClientSide()) return;
        if (entity instanceof Leashable leashable) {
            Leashable.LeashData data = leashable.getLeashData();
            if (data != null && data.leashHolder != null) {
                EnchantedLeadHandler.forget(entity);
            }
        }
    }

    /**
     * A brand-new leash (no existing leash data) starts with no stored lead, clearing anything stale (e.g.
     * copied across a dimension change). Callers that consume a lead record it after this.
     */
    @Inject(method = "setLeashedTo(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Z)V", at = @At("HEAD"))
    private static void terrainlab$forgetOnNewLeash(Entity entity, Entity holder, boolean synch, CallbackInfo ci) {
        if (entity.level().isClientSide()) return;
        if (entity instanceof Leashable leashable && leashable.getLeashData() == null) {
            EnchantedLeadHandler.forget(entity);
        }
    }
}
