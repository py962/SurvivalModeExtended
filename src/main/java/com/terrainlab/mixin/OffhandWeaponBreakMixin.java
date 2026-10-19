package com.terrainlab.mixin;

import com.terrainlab.combat.DualWield;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * During a dual-wield off-hand strike the off-hand weapon sits in the main-hand slot, so a break
 * would broadcast the main-hand break event (particles of the wrong item, on the wrong hand).
 * Report it as an off-hand break instead. Attribute cleanup keeps the real (main-hand) slot.
 */
@Mixin(LivingEntity.class)
public abstract class OffhandWeaponBreakMixin {

    @ModifyArg(
            method = "onEquippedItemBroken",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;entityEventForEquipmentBreak(Lnet/minecraft/world/entity/EquipmentSlot;)B"))
    private EquipmentSlot terrainlab$offhandBreakEvent(EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND
                && (Object) this instanceof Player player
                && DualWield.isOffhandStriking(player)) {
            return EquipmentSlot.OFFHAND;
        }
        return slot;
    }
}
