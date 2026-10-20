package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Shortens the damage cooldown ("invulnerability frames") that a melee hit leaves on a mob from
 * 0.5 s to 0.25 s, so faster attackers (dual wielding, Quickstrike) are not capped at one full hit
 * per 0.5 s.
 *
 * <p>Vanilla gives every hit 20 cooldown ticks and treats the first 10 (cooldown above 10) as the
 * window in which further damage only counts the amount above the last hit. Starting the cooldown
 * at 15 instead leaves a 5-tick (0.25 s) window. Only melee hits on non-player mobs are changed:
 * players keep 0.5 s, and fire, lava, cactus, projectiles, explosions and magic are unchanged, so
 * environmental damage to mobs is not doubled.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class MobMeleeCooldownHandler {
    /** Vanilla starts at 20 ticks; 20 - 15 = 5 ticks (0.25 s) of reduced-damage window. */
    private static final int MELEE_COOLDOWN_TICKS = 15;

    private MobMeleeCooldownHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || target instanceof Player || !isMelee(event.getSource(), target)) {
            return;
        }
        if (event.getContainer().getPostAttackInvulnerabilityTicks() > MELEE_COOLDOWN_TICKS) {
            event.getContainer().setPostAttackInvulnerabilityTicks(MELEE_COOLDOWN_TICKS);
        }
    }

    private static boolean isMelee(DamageSource source, LivingEntity target) {
        return source.getEntity() instanceof LivingEntity attacker
                && source.getDirectEntity() == attacker
                && attacker != target
                && !source.is(DamageTypeTags.IS_PROJECTILE)
                && !source.is(DamageTypeTags.IS_EXPLOSION)
                && !source.is(DamageTypes.THORNS)
                && !source.is(DamageTypes.MAGIC)
                && !source.is(DamageTypes.INDIRECT_MAGIC);
    }
}
