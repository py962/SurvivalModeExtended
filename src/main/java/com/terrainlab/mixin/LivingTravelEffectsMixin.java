package com.terrainlab.mixin;

import com.terrainlab.registry.ModMobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Movement effects in {@code LivingEntity#travelInAir}: Slippery removes ground friction, Phasing removes gravity. */
@Mixin(LivingEntity.class)
public abstract class LivingTravelEffectsMixin {

    // travelInAir multiplies horizontal velocity by (friction * 0.91F) each tick; this makes that factor 1.0.
    private static final float SLIPPERY_FRICTION = 1F / 0.91F;

    @Shadow
    protected abstract double getEffectiveGravity();

    @Redirect(
            method = "travelInAir(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"
            ),
            require = 1
    )
    private float terrainlab$overrideFrictionWhenSlippery(BlockState state, LevelReader level, BlockPos pos, net.minecraft.world.entity.Entity entity) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.hasEffect(ModMobEffects.SLIPPERY)) {
            return SLIPPERY_FRICTION;
        }
        return state.getFriction(level, pos, entity);
    }

    @Redirect(
            method = "travelInAir(Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getEffectiveGravity()D"
            ),
            require = 1
    )
    private double terrainlab$zeroGravityWhenPhasing(LivingEntity self) {
        if (self.hasEffect(ModMobEffects.PHASING)) {
            return 0.0;
        }
        return this.getEffectiveGravity();
    }
}
