package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * Cleave enchantment: adds extra shield durability damage when a melee hit from a Cleave
 * weapon is blocked.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class CleaveShieldHandler {

    private CleaveShieldHandler() {
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity blocker = event.getEntity();

        if (blocker.level().isClientSide()) {
            return;
        }

        if (!event.getBlocked()) {
            return;
        }

        Entity attacker = event.getDamageSource().getEntity();

        if (!(attacker instanceof LivingEntity livingAttacker)) {
            return;
        }

        // Melee only: for projectiles getEntity() is the shooter, not the hand that dealt the hit.
        if (event.getDamageSource().getDirectEntity() != attacker) {
            return;
        }

        ItemStack weapon = livingAttacker.getMainHandItem();

        if (weapon.isEmpty()) {
            return;
        }

        Holder<Enchantment> cleave = blocker.level()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.CLEAVE);

        int level = EnchantmentHelper.getItemEnchantmentLevel(cleave, weapon);

        if (level <= 0) {
            return;
        }

        int additionalShieldDamage = 15 * (level + 1);

        int currentShieldDamage = Math.round(event.shieldDamage());

        event.setShieldDamage(currentShieldDamage + additionalShieldDamage);
    }
}
