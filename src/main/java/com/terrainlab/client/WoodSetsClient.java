package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModEntities;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client registration for the Pine, Azalea and Enderwood wood sets.
 *
 * <p>Signs need nothing here: since 26.3 they are ordinary block models (see the sign
 * blockstates) and vanilla's sign renderers only draw the text.
 *
 * <p>Boats use vanilla's BoatRenderer, which derives its texture from the model layer id:
 * terrainlab:boat/pine -> terrainlab:textures/entity/boat/pine.png.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class WoodSetsClient {

    public static final ModelLayerLocation PINE_BOAT = layer("boat/pine");
    public static final ModelLayerLocation PINE_CHEST_BOAT = layer("chest_boat/pine");
    public static final ModelLayerLocation AZALEA_BOAT = layer("boat/azalea");
    public static final ModelLayerLocation AZALEA_CHEST_BOAT = layer("chest_boat/azalea");
    public static final ModelLayerLocation ENDERWOOD_BOAT = layer("boat/enderwood");
    public static final ModelLayerLocation ENDERWOOD_CHEST_BOAT = layer("chest_boat/enderwood");

    private WoodSetsClient() {
    }

    private static ModelLayerLocation layer(String path) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(TerrainLab.MODID, path), "main");
    }

    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PINE_BOAT, BoatModel::createBoatModel);
        event.registerLayerDefinition(PINE_CHEST_BOAT, BoatModel::createChestBoatModel);
        event.registerLayerDefinition(AZALEA_BOAT, BoatModel::createBoatModel);
        event.registerLayerDefinition(AZALEA_CHEST_BOAT, BoatModel::createChestBoatModel);
        event.registerLayerDefinition(ENDERWOOD_BOAT, BoatModel::createBoatModel);
        event.registerLayerDefinition(ENDERWOOD_CHEST_BOAT, BoatModel::createChestBoatModel);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PINE_BOAT.get(), ctx -> new BoatRenderer(ctx, PINE_BOAT));
        event.registerEntityRenderer(ModEntities.PINE_CHEST_BOAT.get(), ctx -> new BoatRenderer(ctx, PINE_CHEST_BOAT));
        event.registerEntityRenderer(ModEntities.AZALEA_BOAT.get(), ctx -> new BoatRenderer(ctx, AZALEA_BOAT));
        event.registerEntityRenderer(ModEntities.AZALEA_CHEST_BOAT.get(), ctx -> new BoatRenderer(ctx, AZALEA_CHEST_BOAT));
        event.registerEntityRenderer(ModEntities.ENDERWOOD_BOAT.get(), ctx -> new BoatRenderer(ctx, ENDERWOOD_BOAT));
        event.registerEntityRenderer(ModEntities.ENDERWOOD_CHEST_BOAT.get(), ctx -> new BoatRenderer(ctx, ENDERWOOD_CHEST_BOAT));
    }
}
