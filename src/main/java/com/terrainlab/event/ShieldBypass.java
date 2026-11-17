package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * Crouch bypass: a melee hit from a crouching attacker goes straight past a shield, raised or
 * autoblocking, unless the defender is crouching too.
 *
 * <p>The raised-shield case is cancelled at the very start of {@link LivingShieldBlockEvent}, so
 * vanilla sees "not blocked" (full damage, no durability loss, no block sound, no shield
 * disable, no attacker knockback) and every later block listener (bash/thorns, Cleave) sees
 * {@code getBlocked() == false}. Autoblock lives outside that event and checks
 * {@link #isCrouchBypass} itself.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class ShieldBypass {

    private ShieldBypass() {}

    /**
     * Whether this hit ignores the defender's shields: a melee hit (the attacker dealt it
     * directly, no projectile, explosion, thorns or magic) from a crouching living attacker,
     * against a defender that is not crouching.
     */
    public static boolean isCrouchBypass(DamageSource source, LivingEntity defender) {
        if (!(source.getEntity() instanceof LivingEntity attacker)
                || source.getDirectEntity() != attacker
                || attacker == defender) {
            return false;
        }

        if (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypes.THORNS)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return false;
        }

        return attacker.isCrouching() && !defender.isCrouching();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity defender = event.getEntity();
        if (defender.level().isClientSide()
                || !isCrouchBypass(event.getDamageSource(), defender)) {
            return;
        }

        event.setBlocked(false);
        event.setBlockedDamage(0.0f);
    }
}
