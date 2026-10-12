package com.terrainlab.mixin;

import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Scales the horizontal part of a camel's dash (also the Elephant, which extends Camel) by the
 * saddle's Agility level: x1.5 / x2.0 / x2.5. The Agility attribute alone only raises the dash's
 * upward push.
 */
@Mixin(Camel.class)
public abstract class CamelAgilityMixin {

    @ModifyArg(
            method = "executeRidersJump",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"),
            require = 1
    )
    private double terrainlab$agilityDashDistance(double scale) {
        Camel camel = (Camel) (Object) this;
        Holder<Enchantment> agility = ModEnchantments.holder(camel.registryAccess(), ModEnchantments.AGILITY);
        int level = EnchantmentHelper.getItemEnchantmentLevel(agility, camel.getItemBySlot(EquipmentSlot.SADDLE));
        return scale * (1.0 + 0.5 * level);
    }
}
