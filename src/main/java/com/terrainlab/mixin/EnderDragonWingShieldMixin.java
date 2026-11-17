package com.terrainlab.mixin;

import com.terrainlab.event.ShieldCombatEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The dragon's wing swipe push is scaled by a raised shield's knockback rating when within its cover. */
@Mixin(EnderDragon.class)
public abstract class EnderDragonWingShieldMixin {

    @Redirect(
            method = "knockBack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"
            ),
            require = 1
    )
    private void terrainlab$shieldWingPush(Entity entity, double x, double y, double z) {
        if (entity instanceof LivingEntity living) {
            double scale = ShieldCombatEvents.pushScale(living, ((EnderDragon) (Object) this).position());
            if (scale <= 0.0) {
                return;
            }
            entity.push(x * scale, y * scale, z * scale);
            return;
        }

        entity.push(x, y, z);
    }
}
