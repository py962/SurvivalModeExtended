package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModEnchantments;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
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
 * weapon is blocked, by a raised shield or by autoblock.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class CleaveShieldHandler {

    private CleaveShieldHandler() {
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity blocker = event.getEntity();

        if (blocker.level().isClientSide() || !event.getBlocked()) {
            return;
        }

        int additionalShieldDamage = extraShieldDamage(blocker, event.getDamageSource());

        if (additionalShieldDamage > 0) {
            event.setShieldDamage(Math.round(event.shieldDamage()) + additionalShieldDamage);
        }
    }

    /** Extra shield damage from a blocked melee hit: 15 x (level + 1) with a Cleave weapon, else 0. */
    public static int extraShieldDamage(LivingEntity blocker, DamageSource source) {
        Entity attacker = source.getEntity();

        if (!(attacker instanceof LivingEntity livingAttacker)) {
            return 0;
        }

        // Melee only: for projectiles getEntity() is the shooter, not the hand that dealt the hit.
        if (source.getDirectEntity() != attacker) {
            return 0;
        }

        ItemStack weapon = livingAttacker.getMainHandItem();

        if (weapon.isEmpty()) {
            return 0;
        }

        Holder<Enchantment> cleave = blocker.level()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ModEnchantments.CLEAVE);

        int level = EnchantmentHelper.getItemEnchantmentLevel(cleave, weapon);

        return level > 0 ? 15 * (level + 1) : 0;
    }
}
