package com.terrainlab.client.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

import java.util.ArrayList;
import java.util.List;

/** Render state for {@link CelestialForgeRenderer}. */
public class CelestialForgeRenderState extends BlockEntityRenderState {
    public boolean active;
    public boolean transforming;
    /** Ticks into the current phase, with partial tick. */
    public float time;
    /** 0..1 through the transformation. */
    public float transformProgress;
    public int colour = 0xFFFFFF;
    public final ItemStackRenderState gem = new ItemStackRenderState();
    public final List<ItemStackRenderState> components = new ArrayList<>();
}
