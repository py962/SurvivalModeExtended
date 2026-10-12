package com.terrainlab.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Spectral Vision: hostile mobs within {@link #RADIUS} blocks glow through walls,
 * visible only to the affected player. Rendering is decided client-side by
 * SpectralVisionGlowMixin; the effect has no server-side behaviour.
 */
public class SpectralVisionMobEffect extends MobEffect {

    /** Hostiles within this many blocks of the player are outlined. */
    public static final double RADIUS = 32.0;

    public SpectralVisionMobEffect() {
        super(MobEffectCategory.NEUTRAL, 0xD6D6E8); // placeholder pending final palette pass
    }
}
