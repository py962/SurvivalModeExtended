package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.model.CaribouModel;
import com.terrainlab.entity.CaribouEntity;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders the caribou with {@link CaribouModel}, switching to the sheared texture (and
 * hiding the fluff parts) while the entity is in its post-shear regrowth cooldown.
 */
public class CaribouRenderer extends MobRenderer<CaribouEntity, CaribouRenderState, CaribouModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/caribou.png");
    private static final Identifier SHEARED_TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/caribou_sheared.png");

    // Must match CaribouEntity.REGROWTH_COOLDOWN_TICKS (private there).
    private static final long REGROWTH_COOLDOWN_TICKS = 24000L;

    public CaribouRenderer(EntityRendererProvider.Context context) {
        super(context, new CaribouModel(context.bakeLayer(ModModelLayers.CARIBOU)), 0.7F);
    }

    @Override
    public CaribouRenderState createRenderState() {
        return new CaribouRenderState();
    }

    @Override
    public void extractRenderState(CaribouEntity entity, CaribouRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        long lastSheared = entity.getData(ModAttachments.LAST_SHEARED_TIME.get());
        long now = entity.level().getGameTime();
        state.sheared = lastSheared >= 0 && now - lastSheared < REGROWTH_COOLDOWN_TICKS;
    }

    @Override
    public Identifier getTextureLocation(CaribouRenderState state) {
        return state.sheared ? SHEARED_TEXTURE : TEXTURE;
    }
}
