package com.terrainlab.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Write access to LivingEntity's {@code jumping} flag and the attack strength ticker (dual wielding). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("jumping")
    void setJumping(boolean jumping);

    @Accessor("attackStrengthTicker")
    int terrainlab$getAttackStrengthTicker();

    @Accessor("attackStrengthTicker")
    void terrainlab$setAttackStrengthTicker(int ticker);
}
