package com.terrainlab.mixin;

import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.nautilus.AbstractNautilus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Agility on a Nautilus saddle scales the rider's dash burst
 * ({@code AbstractNautilus#executeRidersJump}) by 50% per enchantment level,
 * in water and on land alike.
 */
@Mixin(AbstractNautilus.class)
public abstract class NautilusAgilityMixin {

    @ModifyArg(
        method = "executeRidersJump",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"),
        require = 1
    )
    private double terrainlab$agilityDashBoost(double scale) {
        AbstractNautilus nautilus = (AbstractNautilus) (Object) this;
        ItemStack saddle = nautilus.getItemBySlot(EquipmentSlot.SADDLE);
        Holder<Enchantment> agility =
                ModEnchantments.holder(nautilus.registryAccess(), ModEnchantments.AGILITY);
        int level = EnchantmentHelper.getItemEnchantmentLevel(agility, saddle);
        return scale * (1.0 + 0.5 * level);
    }
}
