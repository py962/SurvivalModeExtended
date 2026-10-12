package com.terrainlab.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Instant effect that detonates an explosion at the affected entity. */
public class ExplosiveMobEffect extends MobEffect {

    // Amplifiable but not extendable; radius in blocks.
    private static final float BASE_RADIUS = 6.0F;
    private static final float RADIUS_PER_AMPLIFIER = 3.0F;

    public ExplosiveMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x0D0D0D);
    }

    @Override
    public boolean isInstantenous() {
        return true;
    }

    // Reached by drinking, splash, tipped arrows and lingering clouds (which re-trigger at their
    // reapplication delay). The blast is credited to the thrower/shooter so kills count as theirs.
    @Override
    public void applyInstantenousEffect(ServerLevel level, Entity source, Entity indirectSource,
                                        LivingEntity entity, int amplifier, double healthModifier) {
        DamageSource damage = indirectSource != null
                ? level.damageSources().explosion(source, indirectSource)
                : null;   // null: vanilla's default explosion source
        explode(level, entity, amplifier, damage);
    }

    // Intended for non-potion routes such as /effect give.
    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        explode(level, entity, amplifier, null);
        return true;
    }

    // No source entity: an explosion skips its source when dealing damage, and the affected
    // entity must be caught in its own blast. Credit comes from the damage source instead.
    private void explode(ServerLevel level, LivingEntity entity, int amplifier, DamageSource damage) {
        float radius = BASE_RADIUS + (RADIUS_PER_AMPLIFIER * amplifier);
        level.explode(null, damage, null, entity.getX(), entity.getY(), entity.getZ(), radius,
                false, Level.ExplosionInteraction.MOB);
    }
}
