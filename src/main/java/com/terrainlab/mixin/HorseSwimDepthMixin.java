package com.terrainlab.mixin;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes horse-like mobs (all {@link AbstractHorse} subclasses, including camels, llamas and the
 * mod's caribou and elephant) swim low in the water, with only the head and shoulders above it.
 * <p>
 * Vanilla floats a mob once water rises above {@code getFluidJumpThreshold()} (0.4 blocks), both for
 * the ridden lift ({@code #can_float_while_ridden}) and for the unridden float goal / swim behaviour,
 * so the mob settles with the surface at about this height above its feet. This raises it to just
 * below the eyes: {@code eyeHeight - HEAD_ALLOWANCE * ageScale} (horse: 1.52 - 0.3 = 1.22, about level
 * with its back). The allowance shrinks with the age scale, so foals still keep their heads above
 * water, and the result is never below vanilla's 0.4. The elephant overrides this with its own value.
 */
@Mixin(AbstractHorse.class)
public abstract class HorseSwimDepthMixin {

    // Blocks of head and shoulders kept above the water for an adult.
    private static final double HEAD_ALLOWANCE = 0.3;

    public double getFluidJumpThreshold() {
        AbstractHorse self = (AbstractHorse) (Object) this;
        double belowEyes = self.getEyeHeight() - HEAD_ALLOWANCE * self.getAgeScale();
        return Math.max(0.4, belowEyes);
    }
}
