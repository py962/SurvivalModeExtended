package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.model.OutlawIllagerModel;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.illager.Pillager;

/**
 * Outlaw Raider renderer: the vanilla illager model with a visible hat brim
 * ({@link OutlawIllagerModel}) and the outlaw texture. Extends IllagerRenderer to bake
 * the custom model, so it re-adds ItemInHandLayer the way PillagerRenderer does.
 */
public class OutlawRaiderRenderer extends IllagerRenderer<Pillager, IllagerRenderState> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/raider/outlaw_pillager.png");

    public OutlawRaiderRenderer(EntityRendererProvider.Context context) {
        super(context, new OutlawIllagerModel(context.bakeLayer(ModModelLayers.OUTLAW_RAIDER_BODY)), 0.5F);
        this.addLayer(new ItemInHandLayer<>(this));
    }

    @Override
    public Identifier getTextureLocation(IllagerRenderState state) {
        return TEXTURE;
    }

    @Override
    public IllagerRenderState createRenderState() {
        return new IllagerRenderState();
    }
}
