package com.terrainlab.client.renderer;

import com.terrainlab.entity.AbstractTrailTraderEntity;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.resources.Identifier;

/**
 * Shared renderer for the four Trail Trader variants: vanilla's VillagerModel on the
 * WANDERING_TRADER layer with a per-variant texture passed to the constructor.
 */
public class TrailTraderRenderer extends MobRenderer<AbstractTrailTraderEntity, VillagerRenderState, VillagerModel> {

    private final Identifier texture;

    public TrailTraderRenderer(EntityRendererProvider.Context context, Identifier texture) {
        super(context, new VillagerModel(context.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5F);
        this.texture = texture;
    }

    @Override
    public VillagerRenderState createRenderState() {
        return new VillagerRenderState();
    }

    @Override
    public Identifier getTextureLocation(VillagerRenderState state) {
        return this.texture;
    }
}
