package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.TieredArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.resources.Identifier;

/**
 * Renders tiered arrows with fletching coloured by feather type: vanilla's arrow texture for
 * normal feathers, and a recoloured copy for owl, goose, vulture and parrot feathers.
 */
public class TieredArrowRenderer extends ArrowRenderer<TieredArrowEntity, TieredArrowRenderer.State> {

    private static final Identifier NORMAL =
            Identifier.withDefaultNamespace("textures/entity/projectiles/arrow.png");
    private static final Identifier OWL = texture("owl");
    private static final Identifier PARROT = texture("parrot");
    private static final Identifier VULTURE = texture("vulture");
    private static final Identifier GOOSE = texture("goose");

    private static Identifier texture(String bird) {
        return Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/" + bird + "_arrow.png");
    }

    public static class State extends ArrowRenderState {
        public TieredArrowEntity.FeatherType feather = TieredArrowEntity.FeatherType.NORMAL;
    }

    public TieredArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TieredArrowEntity arrow, State state, float partialTick) {
        super.extractRenderState(arrow, state, partialTick);
        state.feather = arrow.getFeatherType();
    }

    @Override
    public Identifier getTextureLocation(State state) {
        return switch (state.feather) {
            case OWL -> OWL;
            case PARROT -> PARROT;
            case VULTURE -> VULTURE;
            case GOOSE -> GOOSE;
            default -> NORMAL;
        };
    }
}
