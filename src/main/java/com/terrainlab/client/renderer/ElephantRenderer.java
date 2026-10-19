package com.terrainlab.client.renderer;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.model.ElephantModel;
import com.terrainlab.client.model.HowdahModel;
import com.terrainlab.entity.ElephantEntity;
import com.terrainlab.registry.ModItems;
import com.terrainlab.registry.ModModelLayers;
import net.minecraft.client.model.animal.camel.CamelSaddleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.CamelRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class ElephantRenderer extends AgeableMobRenderer<ElephantEntity, CamelRenderState, ElephantModel> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/elephant.png");

    public ElephantRenderer(EntityRendererProvider.Context context) {
        super(context,
                new ElephantModel(context.bakeLayer(ModModelLayers.ELEPHANT)),
                new ElephantModel(context.bakeLayer(ModModelLayers.ELEPHANT_BABY)),
                1.5F);
        this.addLayer(createSaddleLayer(context));
        this.addLayer(createHowdahLayer(context));
    }

    // Vanilla camel saddle geometry, drawn only when the saddle slot holds a plain Saddle.
    private SimpleEquipmentLayer<CamelRenderState, ElephantModel, CamelSaddleModel> createSaddleLayer(
            EntityRendererProvider.Context context) {
        return new SimpleEquipmentLayer<>(
                this,
                context.getEquipmentRenderer(),
                EquipmentClientInfo.LayerType.CAMEL_SADDLE,
                state -> state.saddle.is(net.minecraft.world.item.Items.SADDLE) ? state.saddle : ItemStack.EMPTY,
                new CamelSaddleModel(context.bakeLayer(ModelLayers.CAMEL_SADDLE)),
                null
        );
    }

    // Howdah geometry, drawn only when the saddle slot holds a Howdah. Shares the
    // CAMEL_SADDLE layer type; the two layers never draw together.
    private SimpleEquipmentLayer<CamelRenderState, ElephantModel, HowdahModel> createHowdahLayer(
            EntityRendererProvider.Context context) {
        return new SimpleEquipmentLayer<>(
                this,
                context.getEquipmentRenderer(),
                EquipmentClientInfo.LayerType.CAMEL_SADDLE,
                state -> state.saddle.is(ModItems.HOWDAH.get()) ? state.saddle : ItemStack.EMPTY,
                new HowdahModel(context.bakeLayer(ModModelLayers.HOWDAH)),
                null
        );
    }

    @Override
    public Identifier getTextureLocation(CamelRenderState state) {
        return TEXTURE;
    }

    @Override
    public CamelRenderState createRenderState() {
        return new CamelRenderState();
    }

    @Override
    public void extractRenderState(ElephantEntity entity, CamelRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.saddle = entity.getItemBySlot(EquipmentSlot.SADDLE).copy();
    }
}
