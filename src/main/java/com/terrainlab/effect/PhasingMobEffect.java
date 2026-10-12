package com.terrainlab.effect;

import com.terrainlab.event.PhasingEffectHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Phasing: grants noclip (noPhysics) while active. This class switches it on when
 * the effect is added; {@link PhasingEffectHandler} enforces it each tick and
 * restores the entity's original value when the effect ends.
 */
public class PhasingMobEffect extends MobEffect {
    public PhasingMobEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xEF83F0);
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        super.onEffectAdded(entity, amplifier);
        PhasingEffectHandler.rememberOriginal(entity);
        entity.noPhysics = true;
    }

    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        super.onEffectStarted(entity, amplifier);
        PhasingEffectHandler.rememberOriginal(entity);
        entity.noPhysics = true;
    }

    @Override
    public void onMobRemoved(ServerLevel level, LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        PhasingEffectHandler.restoreOriginal(entity);
        super.onMobRemoved(level, entity, amplifier, reason);
    }
}
