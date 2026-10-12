package com.terrainlab.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Snowballs thrown by snow golems deal {@link #BUFF_AMOUNT} extra damage. */
@Mixin(Snowball.class)
public abstract class SnowGolemDamageMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/SnowGolemDamage]";

    private static final float BUFF_AMOUNT = 5.0F;

    @Redirect(
            method = "onHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"
            ),
            require = 1
    )
    private void terrainlab$buffSnowGolemDamage(Entity entity, DamageSource source, float amount) {
        Snowball self = (Snowball) (Object) this;
        boolean thrownBySnowGolem = self.getOwner() instanceof SnowGolem;

        float finalAmount = amount;
        if (thrownBySnowGolem) {
            finalAmount = amount + BUFF_AMOUNT;
        }

        float healthBefore = entity instanceof LivingEntity living ? living.getHealth() : -1;

        // LOGGER.trace(
                // "{} redirect fired: target={}, ownerIsSnowGolem={}, baseAmount={}, finalAmount={}, healthBefore={}",
                // LOG_PREFIX, entity.getType(), thrownBySnowGolem, amount, finalAmount, healthBefore
        // );

        entity.hurt(source, finalAmount);

        float healthAfter = entity instanceof LivingEntity living ? living.getHealth() : -1;
        // LOGGER.trace("{} healthAfter={} (delta={})",
                // LOG_PREFIX, healthAfter, healthBefore >= 0 ? healthBefore - healthAfter : -1);
    }
}
