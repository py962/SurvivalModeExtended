package com.terrainlab.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a Horse wearing Leather Horse Armor walk on Powder Snow, mirroring
 * vanilla's leather-boots rule for players.
 * <p>
 * Targets the static {@code PowderSnowBlock#canEntityWalkOnPowderSnow}, the single
 * check used by both the collision shape and the climb-out logic; vanilla only
 * inspects the FEET slot, which horses do not have.
 */
@Mixin(PowderSnowBlock.class)
public class HorseLeatherArmorPowderSnowMixin {

    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void terrainlab$leatherArmorPreventsSinking(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Horse horse && horse.getItemBySlot(EquipmentSlot.BODY).is(Items.LEATHER_HORSE_ARMOR)) {
            cir.setReturnValue(true);
        }
    }
}
