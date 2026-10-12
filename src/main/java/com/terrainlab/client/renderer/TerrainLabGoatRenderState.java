package com.terrainlab.client.renderer;

import net.minecraft.client.renderer.entity.state.GoatRenderState;

/** Vanilla goat render state plus a {@code sheared} flag for the texture and fluff switch. */
public class TerrainLabGoatRenderState extends GoatRenderState {
    public boolean sheared;
}
