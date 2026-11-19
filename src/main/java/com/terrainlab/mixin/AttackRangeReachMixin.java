package com.terrainlab.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.AttackRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity reach bonuses (Reach) extend item attack ranges such as the spear's for survival players.
 * The default range already uses the full interaction range, so it is built from the base value instead.
 */
@Mixin(AttackRange.class)
public abstract class AttackRangeReachMixin {

    @Inject(method = "effectiveMaxRange", at = @At("RETURN"), cancellable = true, require = 1)
    private void terrainlab$reachBonus(Entity entity, CallbackInfoReturnable<Float> cir) {
        float bonus = terrainlab$bonus(entity);
        if (bonus != 0.0F) {
            cir.setReturnValue(cir.getReturnValueF() + bonus);
        }
    }

    @Inject(method = "defaultFor", at = @At("RETURN"), cancellable = true, require = 1)
    private static void terrainlab$baseDefault(LivingEntity entity, CallbackInfoReturnable<AttackRange> cir) {
        float bonus = terrainlab$bonus(entity);
        if (bonus != 0.0F) {
            AttackRange range = cir.getReturnValue();
            cir.setReturnValue(new AttackRange(range.minReach(), range.maxReach() - bonus, range.minCreativeReach(),
                    range.maxCreativeReach() - bonus, range.hitboxMargin(), range.mobFactor()));
        }
    }

    private static float terrainlab$bonus(Entity entity) {
        if (!(entity instanceof Player player) || player.isCreative()) {
            return 0.0F;
        }
        return (float) (player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
                - player.getAttributeBaseValue(Attributes.ENTITY_INTERACTION_RANGE));
    }
}
