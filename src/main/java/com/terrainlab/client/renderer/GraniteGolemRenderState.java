package com.terrainlab.client.renderer;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;


public class GraniteGolemRenderState extends LivingEntityRenderState {
    public boolean isTunnelling = false;
    public boolean eyesGlowing = false;
    public int animTick = 0;
}