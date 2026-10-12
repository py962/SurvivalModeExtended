package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.model.TerrainLabGoatModel;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.goat.Goat;

/**
 * Goat renderer matching vanilla's GoatRenderer, but baking {@link TerrainLabGoatModel}
 * (with the fluff part) and switching to the sheared texture during the regrowth
 * cooldown. The baby model is baked from vanilla's GOAT_BABY layer and has no fluff.
 */
public class TerrainLabGoatRenderer extends AgeableMobRenderer<Goat, TerrainLabGoatRenderState, TerrainLabGoatModel> {

    private static final Identifier GOAT_LOCATION = Identifier.withDefaultNamespace("textures/entity/goat/goat.png");
    private static final Identifier SHEARED_TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/goat_sheared.png");

    // Must match CaribouEntity/GoatShearHandler's REGROWTH_COOLDOWN_TICKS.
    private static final long REGROWTH_COOLDOWN_TICKS = 24000L;

    public TerrainLabGoatRenderer(EntityRendererProvider.Context context) {
        super(context,
                new TerrainLabGoatModel(context.bakeLayer(ModModelLayers.GOAT)),
                new TerrainLabGoatModel(context.bakeLayer(ModelLayers.GOAT_BABY)),
                0.7F);
    }

    @Override
    public TerrainLabGoatRenderState createRenderState() {
        return new TerrainLabGoatRenderState();
    }

    @Override
    public void extractRenderState(Goat entity, TerrainLabGoatRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.hasLeftHorn = entity.hasLeftHorn();
        state.hasRightHorn = entity.hasRightHorn();
        state.rammingXHeadRot = entity.getRammingXHeadRot();

        long lastSheared = entity.getData(ModAttachments.LAST_SHEARED_TIME.get());
        long now = entity.level().getGameTime();
        state.sheared = lastSheared >= 0 && now - lastSheared < REGROWTH_COOLDOWN_TICKS;
    }

    @Override
    public Identifier getTextureLocation(TerrainLabGoatRenderState state) {
        return state.sheared ? SHEARED_TEXTURE : GOAT_LOCATION;
    }
}
