package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.block.ModWoodTypes;
import com.terrainlab.registry.ModEntities;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client registration for the Pine, Azalea, Poplar and Enderwood wood sets, including the poplar
 * falling-leaf particles (26.3's poplar leaves particle uses the same motion as pale oak's).
 *
 * <p>Signs need no renderer of their own: vanilla's sign and hanging sign renderers build a
 * model for every registered WoodType, and the sign atlas lists every namespace's
 * textures/entity/signs. Sheets only snapshots the vanilla wood types, so ours are added here.
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
    public static final ModelLayerLocation POPLAR_BOAT = layer("boat/poplar");
    public static final ModelLayerLocation POPLAR_CHEST_BOAT = layer("chest_boat/poplar");
    public static final ModelLayerLocation ENDERWOOD_BOAT = layer("boat/enderwood");
    public static final ModelLayerLocation ENDERWOOD_CHEST_BOAT = layer("chest_boat/enderwood");

    private WoodSetsClient() {
    }

    private static ModelLayerLocation layer(String path) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath(TerrainLab.MODID, path), "main");
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Sheets.addWoodType(ModWoodTypes.PINE);
            Sheets.addWoodType(ModWoodTypes.AZALEA);
            Sheets.addWoodType(ModWoodTypes.ENDERWOOD);
            Sheets.addWoodType(ModWoodTypes.POPLAR);
        });
    }

    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PINE_BOAT, BoatModel::createBoatModel);
        event.registerLayerDefinition(PINE_CHEST_BOAT, BoatModel::createChestBoatModel);
        event.registerLayerDefinition(AZALEA_BOAT, BoatModel::createBoatModel);
        event.registerLayerDefinition(AZALEA_CHEST_BOAT, BoatModel::createChestBoatModel);
        event.registerLayerDefinition(POPLAR_BOAT, BoatModel::createBoatModel);
        event.registerLayerDefinition(POPLAR_CHEST_BOAT, BoatModel::createChestBoatModel);
        event.registerLayerDefinition(ENDERWOOD_BOAT, BoatModel::createBoatModel);
        event.registerLayerDefinition(ENDERWOOD_CHEST_BOAT, BoatModel::createChestBoatModel);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PINE_BOAT.get(), ctx -> new BoatRenderer(ctx, PINE_BOAT));
        event.registerEntityRenderer(ModEntities.PINE_CHEST_BOAT.get(), ctx -> new BoatRenderer(ctx, PINE_CHEST_BOAT));
        event.registerEntityRenderer(ModEntities.AZALEA_BOAT.get(), ctx -> new BoatRenderer(ctx, AZALEA_BOAT));
        event.registerEntityRenderer(ModEntities.AZALEA_CHEST_BOAT.get(), ctx -> new BoatRenderer(ctx, AZALEA_CHEST_BOAT));
        event.registerEntityRenderer(ModEntities.POPLAR_BOAT.get(), ctx -> new BoatRenderer(ctx, POPLAR_BOAT));
        event.registerEntityRenderer(ModEntities.POPLAR_CHEST_BOAT.get(), ctx -> new BoatRenderer(ctx, POPLAR_CHEST_BOAT));
        event.registerEntityRenderer(ModEntities.ENDERWOOD_BOAT.get(), ctx -> new BoatRenderer(ctx, ENDERWOOD_BOAT));
        event.registerEntityRenderer(ModEntities.ENDERWOOD_CHEST_BOAT.get(), ctx -> new BoatRenderer(ctx, ENDERWOOD_CHEST_BOAT));
    }

    @SubscribeEvent
    static void onRegisterParticleProviders(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.terrainlab.registry.ModParticles.RED_POPLAR_LEAVES.get(), net.minecraft.client.particle.FallingLeavesParticle.PaleOakProvider::new);
        event.registerSpriteSet(com.terrainlab.registry.ModParticles.ORANGE_POPLAR_LEAVES.get(), net.minecraft.client.particle.FallingLeavesParticle.PaleOakProvider::new);
        event.registerSpriteSet(com.terrainlab.registry.ModParticles.YELLOW_POPLAR_LEAVES.get(), net.minecraft.client.particle.FallingLeavesParticle.PaleOakProvider::new);
    }
}
