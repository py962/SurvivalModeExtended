package com.terrainlab.client.cushion;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public class CushionRenderState extends EntityRenderState {
    public Direction direction = Direction.NORTH;
    public Identifier texture = CushionRenderer.texture(net.minecraft.world.item.DyeColor.WHITE);
}
