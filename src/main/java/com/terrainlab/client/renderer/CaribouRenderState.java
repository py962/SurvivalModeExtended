package com.terrainlab.client.renderer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Caribou render state; {@code sheared} is true during the post-shear regrowth cooldown. */
public class CaribouRenderState extends LivingEntityRenderState {
    public boolean sheared;
}
