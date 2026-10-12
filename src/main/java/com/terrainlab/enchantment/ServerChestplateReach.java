package com.terrainlab.enchantment;

import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Server-side block placement range check that extends reach by the Reach enchantment on the chestplate. */
public final class ServerChestplateReach {

    private ServerChestplateReach() {
    }

    /**
     * Returns whether {@code pos} is within placement range, adding 0.5 blocks per Reach level.
     * {@code normalRange} is the vanilla buffer added on top of the player's interaction range.
     */
    public static boolean isWithinPlacementRange(
            ServerPlayer player,
            net.minecraft.core.BlockPos pos,
            double normalRange
    ) {
        if (player.isWithinBlockInteractionRange(pos, normalRange)) {
            return true;
        }

        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);

        if (chestplate.isEmpty()) {
            return false;
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
            return false;
        }

        double additionalRange = 0.5D * level;

        return player.isWithinBlockInteractionRange(
                pos,
                normalRange + additionalRange
        );
    }
}
