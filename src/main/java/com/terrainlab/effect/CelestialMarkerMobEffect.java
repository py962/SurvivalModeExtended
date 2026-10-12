package com.terrainlab.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Cosmetic marker effect that shows an icon for an active Celestial amulet or
 * conduit bonus; the real effects are applied by CelestialEffects. One instance
 * is registered per variant for amulets and for conduits, coloured by the variant's tint.
 */
public class CelestialMarkerMobEffect extends MobEffect {
    public CelestialMarkerMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
