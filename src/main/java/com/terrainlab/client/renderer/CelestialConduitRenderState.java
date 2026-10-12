package com.terrainlab.client.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

/**
 * Render state for {@link CelestialConduitRenderer}. Mirrors vanilla's conduit render
 * state, with {@code isFullPower} in place of isHunting and a per-variant tint.
 */
public class CelestialConduitRenderState extends BlockEntityRenderState {
    public boolean isActive;
    public float activeRotation;
    public float animTime;
    public int animationPhase;
    public boolean isFullPower;
    /** Packed 0xRRGGBB variant tint; white if the variant cannot be resolved. */
    public int tintColor = 0xFFFFFF;
}
