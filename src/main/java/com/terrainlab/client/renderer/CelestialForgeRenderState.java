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
    /** Ghost of the wanted component, shown now and then during the ritual as a clue. */
    public final ItemStackRenderState clue = new ItemStackRenderState();
    /** 0..1 size of the clue; 0 while hidden. */
    public float clueScale;
    public final List<ItemStackRenderState> components = new ArrayList<>();
}
