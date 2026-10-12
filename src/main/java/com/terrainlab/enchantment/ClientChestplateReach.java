        package com.terrainlab.enchantment;

import com.terrainlab.registry.ModEnchantments;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Client-side block placement range bonus from the Reach enchantment on the worn chestplate.
 */
public final class ClientChestplateReach {

    private ClientChestplateReach() {
    }

    public static double getPlacementRange(
            LocalPlayer player,
            double normalRange
    ) {
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);

        if (chestplate.isEmpty()) {
            return normalRange;
        }

        Holder<Enchantment> reach = player.level()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.REACH);

        int level = EnchantmentHelper.getItemEnchantmentLevel(
                reach,
                chestplate
        );

        if (level <= 0) {
            return normalRange;
        }

        return normalRange + (0.5D * level);
    }
}
