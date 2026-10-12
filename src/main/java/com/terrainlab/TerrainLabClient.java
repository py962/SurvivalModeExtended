package com.terrainlab;

import com.terrainlab.client.model.GhostFishModel;
import com.terrainlab.client.model.GraniteGolemModel;
import com.terrainlab.client.model.HouseboatModel;
import com.terrainlab.client.model.CaribouModel;
import com.terrainlab.client.model.TerrainLabGoatModel;
import com.terrainlab.client.model.OwlModel;
import com.terrainlab.client.renderer.GhostFishRenderer;
import com.terrainlab.client.renderer.GraniteGolemRenderer;
import com.terrainlab.client.renderer.HouseboatRenderer;
import com.terrainlab.client.renderer.FurCloakRenderer;
import com.terrainlab.client.renderer.CelestialAmuletRenderer;
import com.terrainlab.client.model.CelestialAmuletModel;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.client.renderer.TintedShieldRenderer;
import com.terrainlab.client.model.HowdahModel;
import com.terrainlab.registry.ModBlockEntities;
import com.terrainlab.registry.ModEntities;
import com.terrainlab.registry.ModItems;
import com.terrainlab.registry.ModMenuTypes;
import com.terrainlab.registry.ModModelLayers;
import com.terrainlab.client.renderer.CelestialConduitRenderer;
import com.terrainlab.screen.AncientCodexScreen;
import com.terrainlab.screen.ReplicatorScreen;
import com.terrainlab.screen.VillageInfoScreen;
import com.terrainlab.screen.CelestialForgeScreen;
import com.terrainlab.client.renderer.FrostbittenRenderer;
import com.terrainlab.client.renderer.CaribouRenderer;
import com.terrainlab.client.renderer.TerrainLabGoatRenderer;
import com.terrainlab.client.renderer.OwlRenderer;
import com.terrainlab.client.renderer.GrizzlyBearRenderer;
import com.terrainlab.client.renderer.ElephantRenderer;
import com.terrainlab.client.model.ElephantModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import com.terrainlab.screen.FletchingTableScreen;
import com.terrainlab.client.renderer.TieredArrowRenderer;
import com.terrainlab.client.model.GooseModel;
import com.terrainlab.client.model.VultureModel;
import com.terrainlab.client.renderer.GooseRenderer;
import com.terrainlab.client.renderer.VultureRenderer;
import com.terrainlab.client.renderer.TrailTraderRenderer;
import com.terrainlab.client.TrailTraderDialogueOverlay;
import net.neoforged.neoforge.common.NeoForge;
import com.terrainlab.client.model.PirateGearModel;
import com.terrainlab.client.model.PirateEyepatchModel;
import com.terrainlab.client.model.OutlawIllagerModel;
import com.terrainlab.client.model.OutlawBandanaModel;
import com.terrainlab.client.model.TrapperHatModel;
import com.terrainlab.client.renderer.PirateRaiderRenderer;
import com.terrainlab.client.renderer.OutlawRaiderRenderer;
import com.terrainlab.client.renderer.TrapperRaiderRenderer;
import top.theillusivec4.curios.api.client.ICurioRenderer;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import com.terrainlab.client.hud.TerrainLabDebugEntry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Set;

/** Client entry point: registers renderers, model layers, screens, item-model properties and Curios renderers. */
@Mod(value = TerrainLab.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public class TerrainLabClient {

    public TerrainLabClient(ModContainer container) {
    }


    @SubscribeEvent
    static void onRegisterDebugEntries(RegisterDebugEntriesEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(TerrainLab.MODID, "debug_info");
        event.register(id, new TerrainLabDebugEntry());
        event.includeInProfile(id, DebugScreenProfile.DEFAULT, DebugScreenEntryStatus.ALWAYS_ON);
    }

    // Wood shields: "terrainlab:tinted_shield" special model renderer, vanilla's shield mesh tinted
    // with an exact RGB. The oak shield stays on "minecraft:shield".
    @SubscribeEvent
    static void onRegisterSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "tinted_shield"),
                TintedShieldRenderer.Unbaked.MAP_CODEC
        );
    }

    // Additive "glow" GUI pipeline used by the Ancient Codex and Celestial
    // Forge screens (see CodexPipelines).
    @SubscribeEvent
    static void onRegisterRenderPipelines(net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) {
        event.registerPipeline(com.terrainlab.client.CodexPipelines.GUI_GLOW);
    }

    // Wood shields: "terrainlab:shield_raised" item-model condition (using_item, plus a short
    // flash after a passive auto-block).
    @SubscribeEvent
    static void onRegisterConditionalItemModelProperties(
            net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(TerrainLab.MODID, "shield_raised"),
                com.terrainlab.client.item.properties.ShieldRaisedProperty.MAP_CODEC
        );
    }

    // Wood shields: third-person BLOCK arm pose during a passive auto-block flash; null falls
    // through to vanilla.
    @SubscribeEvent
    static void onRegisterClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        net.minecraft.world.item.Item[] shields = ModItems.SHIELD_ITEMS.values().stream()
                .map(net.neoforged.neoforge.registries.DeferredItem::get)
                .toArray(net.minecraft.world.item.Item[]::new);
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(net.minecraft.world.entity.LivingEntity entity,
                                                    net.minecraft.world.InteractionHand hand,
                                                    net.minecraft.world.item.ItemStack stack) {
                return com.terrainlab.client.ClientShieldAutoBlockState.isFlashing(entity, hand)
                        ? HumanoidModel.ArmPose.BLOCK
                        : null;
            }
        }, shields);
    }

    @SubscribeEvent
    static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ModModelLayers.HOUSEBOAT,
                HouseboatModel::createHouseboatModel);
        event.registerLayerDefinition(ModModelLayers.HOUSEBOAT_WATER_PATCH,
                HouseboatModel::createWaterPatch);
        event.registerLayerDefinition(ModModelLayers.HOWDAH,
                HowdahModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.GRANITE_GOLEM,
                GraniteGolemModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.GHOST_FISH,
                GhostFishModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.CARIBOU,
                CaribouModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.GOAT,
                TerrainLabGoatModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.OWL,
                OwlModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.ELEPHANT,
                ElephantModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.GOOSE,
                GooseModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.VULTURE,
                VultureModel::createBodyLayer);

        // Pillager raider gear layers.
        event.registerLayerDefinition(ModModelLayers.PIRATE_BANDANA,
                PirateGearModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.PIRATE_EYEPATCH,
                PirateEyepatchModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.OUTLAW_RAIDER_BODY,
                OutlawIllagerModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.OUTLAW_BANDANA,
                OutlawBandanaModel::createBodyLayer);
        event.registerLayerDefinition(ModModelLayers.TRAPPER_HAT,
                TrapperHatModel::createBodyLayer);

        // Frostbitten and Grizzly Bear reuse vanilla models.

        // Fur cloak: mantle, collar, cape and shoulder caps (see FurCloakModel).
        event.registerLayerDefinition(ModModelLayers.FUR_CLOAK, com.terrainlab.client.model.FurCloakModel::createBodyLayer);

        // Curios renderers are registered here rather than in AddLayers: Curios snapshots its
        // renderer map at the end of its own AddLayers handler, and RegisterLayerDefinitions
        // always fires before that regardless of mod ordering.
        // TerrainLab.LOGGER.info("[FUR_CLOAK_REGISTER] onRegisterLayerDefinitions firing, registering FurCloakRenderer for {}", ModItems.FUR_CLOAK.get());
        try {
            ICurioRenderer.register(ModItems.FUR_CLOAK.get(), FurCloakRenderer::new);
            // TerrainLab.LOGGER.info("[FUR_CLOAK_REGISTER] register() call completed without exception");
        } catch (Throwable t) {
            TerrainLab.LOGGER.error("[FUR_CLOAK_REGISTER] register() threw!", t);
        }

        // Celestial amulets: one shared pendant layer for all variants; renderers registered
        // here for the same Curios ordering reason as the fur cloak.
        event.registerLayerDefinition(ModModelLayers.CELESTIAL_AMULET,
                CelestialAmuletModel::createBodyLayer);
        for (CelestialVariant variant : CelestialVariant.values()) {
            // TerrainLab.LOGGER.info("[CELESTIAL_AMULET_REGISTER] registering CelestialAmuletRenderer for {}",
                    // ModItems.amuletFor(variant));
            try {
                ICurioRenderer.register(ModItems.amuletFor(variant), CelestialAmuletRenderer::new);
            } catch (Throwable t) {
                TerrainLab.LOGGER.error("[CELESTIAL_AMULET_REGISTER] register() threw for {}!", variant, t);
            }
        }
    }

    @SubscribeEvent
    static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.HOUSEBOAT.get(),
                HouseboatRenderer::new);
        event.registerEntityRenderer(ModEntities.GRANITE_GOLEM.get(),
                GraniteGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.GHOST_FISH.get(),
                GhostFishRenderer::new);
        event.registerEntityRenderer(ModEntities.VOID_GLASS_FALLING.get(),
                net.minecraft.client.renderer.entity.FallingBlockRenderer::new);
        event.registerEntityRenderer(ModEntities.TIERED_ARROW.get(), TieredArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.FROSTBITTEN.get(),
                FrostbittenRenderer::new);
        event.registerEntityRenderer(ModEntities.CARIBOU.get(),
                CaribouRenderer::new);
        event.registerEntityRenderer(ModEntities.OWL.get(),
                OwlRenderer::new);
        event.registerEntityRenderer(ModEntities.GRIZZLY_BEAR.get(),
                GrizzlyBearRenderer::new);
        event.registerEntityRenderer(ModEntities.ELEPHANT.get(),
                ElephantRenderer::new);
        event.registerEntityRenderer(ModEntities.GOOSE.get(),
                GooseRenderer::new);
        event.registerEntityRenderer(ModEntities.VULTURE.get(),
                VultureRenderer::new);

        // Vanilla goat gets a renderer with the sheared fluff layer; entity and AI stay vanilla.
        event.registerEntityRenderer(EntityType.GOAT,
                TerrainLabGoatRenderer::new);

        // Trail Traders share one renderer (vanilla wandering-trader model) with a per-variant
        // texture.
        event.registerEntityRenderer(ModEntities.RANGER.get(),
                context -> new TrailTraderRenderer(context,
                        Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/ranger.png")));
        event.registerEntityRenderer(ModEntities.ARCHEOLOGIST.get(),
                context -> new TrailTraderRenderer(context,
                        Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/archeologist.png")));
        event.registerEntityRenderer(ModEntities.MARINER.get(),
                context -> new TrailTraderRenderer(context,
                        Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/mariner.png")));
        event.registerEntityRenderer(ModEntities.PRIEST.get(),
                context -> new TrailTraderRenderer(context,
                        Identifier.fromNamespaceAndPath(TerrainLab.MODID, "textures/entity/priest.png")));

        // Raider variants: vanilla Pillager model plus gear layers.
        event.registerEntityRenderer(ModEntities.PIRATE_RAIDER.get(),
                PirateRaiderRenderer::new);
        event.registerEntityRenderer(ModEntities.OUTLAW_RAIDER.get(),
                OutlawRaiderRenderer::new);
        event.registerEntityRenderer(ModEntities.TRAPPER_RAIDER.get(),
                TrapperRaiderRenderer::new);

        // Celestial conduits: one renderer for all variants, recoloured per variant.
        event.registerBlockEntityRenderer(ModBlockEntities.CELESTIAL_CONDUIT_BE.get(), CelestialConduitRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CELESTIAL_FORGE_BE.get(),
                com.terrainlab.client.renderer.CelestialForgeRenderer::new);
    }

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ANCIENT_CODEX_MENU.get(), AncientCodexScreen::new);
        event.register(ModMenuTypes.REPLICATOR_MENU.get(), ReplicatorScreen::new);
        event.register(ModMenuTypes.FLETCHING_TABLE_MENU.get(), FletchingTableScreen::new);
        event.register(ModMenuTypes.VILLAGE_INFO_MENU.get(), VillageInfoScreen::new);
        event.register(ModMenuTypes.CELESTIAL_FORGE_MENU.get(), CelestialForgeScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // TrailTraderDialogueOverlay is not an @EventBusSubscriber, so its listener is added manually.
        NeoForge.EVENT_BUS.addListener(TrailTraderDialogueOverlay::onScreenRenderPost);
    }
}
