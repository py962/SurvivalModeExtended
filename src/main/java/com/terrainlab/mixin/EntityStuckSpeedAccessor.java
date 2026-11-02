package com.terrainlab.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the Freezing effect release the powder-snow drag for the tick a player jumps. */
@Mixin(Entity.class)
public interface EntityStuckSpeedAccessor {
    @Accessor("stuckSpeedMultiplier")
    void terrainlab$setStuckSpeedMultiplier(Vec3 multiplier);
}
