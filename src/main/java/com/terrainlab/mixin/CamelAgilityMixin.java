
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
 * Increases only the horizontal distance of a camel's dash according to
 * the Agility enchantment on its saddle.
 *
 * Agility I:   1.5x horizontal distance
 * Agility II:  2.0x horizontal distance
 * Agility III: 2.5x horizontal distance
 *
 * The vanilla vertical jump momentum is left unchanged.
 */
@Mixin(Camel.class)
public abstract class CamelAgilityMixin {

    @ModifyArg(
            method = "executeRidersJump",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"
            ),
            index = 0,
            require = 1
    )
    private double terrainlab$agilityDashDistance(double horizontalMomentum) {
        Camel camel = (Camel) (Object) this;

        Holder<Enchantment> agility = ModEnchantments.holder(
                camel.registryAccess(),
                ModEnchantments.AGILITY
        );

        int level = EnchantmentHelper.getItemEnchantmentLevel(
                agility,
                camel.getItemBySlot(EquipmentSlot.SADDLE)
        );

        double multiplier = 1.0 + (0.5 * level);

        return horizontalMomentum * multiplier;
    }
}
