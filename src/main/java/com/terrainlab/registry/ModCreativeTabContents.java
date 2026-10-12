package com.terrainlab.registry;

import com.terrainlab.celestial.CelestialVariant;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Adds TerrainLab items to the matching vanilla creative tabs (no dedicated
 * mod tab), placed where a player would look for the equivalent vanilla item.
 */
public class ModCreativeTabContents {

    public static void register(IEventBus eventBus) {
        eventBus.addListener(ModCreativeTabContents::buildContents);
    }

    private static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModItems.ENDERWOOD);

        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModItems.ENDERVINE);
            event.accept(ModItems.SCULK_FLOWER);
            event.accept(ModItems.NIGHTCAP_MUSHROOM);
            event.accept(ModItems.MAGNETITE_ORE);
            event.accept(ModItems.VOID_GLASS);
            event.accept(ModItems.SAND_LAYER);
            event.accept(ModItems.RED_SAND_LAYER);

        } else if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.MAGNETITE_DUST);
            event.accept(ModItems.FIELD_STONE);
            event.accept(ModItems.REPLICATOR);
            event.accept(ModItems.RANGEFINDER_COMPASS);
            event.accept(ModItems.BUBBLE_GAUGE);
            event.accept(ModItems.CALENDAR);
            event.accept(ModItems.THERMOMETER);

        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            // The mod's Ominous Banner is already here: vanilla's own slot builds it (RaidOminousBannerMixin).
            event.accept(ModItems.FLETCHING_TABLE_ITEM);
            event.accept(ModItems.CELESTIAL_FORGE_ITEM);
            for (CelestialVariant variant : CelestialVariant.values()) {
                if (variant.usesVanillaConduit()) continue; // vanilla Conduit is already in this tab
                event.accept(ModItems.CELESTIAL_CONDUIT_ITEMS.get(variant));
            }

        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.HOWDAH);
            event.accept(ModItems.HOUSEBOAT_ITEM);
            event.accept(ModItems.GHOST_FISH_BUCKET);
            event.accept(ModItems.FUR_CLOAK);
            event.accept(ModItems.ATLAS);
            for (CelestialVariant variant : CelestialVariant.values()) {
                event.accept(ModItems.CELESTIAL_AMULETS.get(variant));
            }

        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.VULTURE_TIERED_ARROW);
            event.accept(ModItems.GOOSE_TIERED_ARROW);
            event.accept(ModItems.NORMAL_TIERED_ARROW);
            event.accept(ModItems.OWL_TIERED_ARROW);
            event.accept(ModItems.PARROT_TIERED_ARROW);
            for (DeferredItem<Item> shield : ModItems.SHIELD_ITEMS.values()) {
                event.accept(shield);
            }

        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModItems.EXPERIENCE_BOTTLE);
            event.accept(ModItems.LEATHER_FLASK);
            event.accept(ModItems.COOKED_VENISON);
            event.accept(ModItems.VENISON);

        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.ANCIENT_ARTEFACT);
            event.accept(ModItems.VOID_SHARDS);
            event.accept(ModItems.NETHER_IDOL);
            event.accept(ModItems.OWL_FEATHER);
            event.accept(ModItems.PARROT_FEATHER);
            event.accept(ModItems.VULTURE_FEATHER);
            event.accept(ModItems.GOOSE_FEATHER);
            event.accept(ModItems.GOAT_FUR);
            event.accept(ModItems.CARIBOU_FUR);
            event.accept(ModItems.GHOST_FISH_SCALE);

            for (CelestialVariant variant : CelestialVariant.values()) {
                event.accept(ModItems.CELESTIAL_PEARLS.get(variant));
            }

        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.GHOST_FISH_SPAWN_EGG);
            event.accept(ModItems.FROSTBITTEN_SPAWN_EGG);
            event.accept(ModItems.CARIBOU_SPAWN_EGG);
            event.accept(ModItems.OWL_SPAWN_EGG);
            event.accept(ModItems.VULTURE_SPAWN_EGG);
            event.accept(ModItems.GOOSE_SPAWN_EGG);
            event.accept(ModItems.GRIZZLY_BEAR_SPAWN_EGG);
            event.accept(ModItems.RANGER_SPAWN_EGG);
            event.accept(ModItems.ARCHEOLOGIST_SPAWN_EGG);
            event.accept(ModItems.MARINER_SPAWN_EGG);
            event.accept(ModItems.PRIEST_SPAWN_EGG);
        }
    }
}
