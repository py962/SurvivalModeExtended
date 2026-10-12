package com.terrainlab.mixin;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes horse-like mobs (all {@link AbstractHorse} subclasses, including camels, llamas and the
 * mod's caribou and elephant) swim chest-deep instead of skimming the surface.
 * <p>
 * Vanilla floats a mob once water rises above {@code getFluidJumpThreshold()} (0.4 blocks). This
 * raises it to 1 block, but keeps it at least 0.35 blocks below the eyes so foals' heads stay above
 * water, and never below vanilla's 0.4.
 */
@Mixin(AbstractHorse.class)
public abstract class HorseSwimDepthMixin {

    public double getFluidJumpThreshold() {
        AbstractHorse self = (AbstractHorse) (Object) this;
        double belowEyes = self.getEyeHeight() - 0.35;
        return Math.max(0.4, Math.min(1.0, belowEyes));
    }
}
