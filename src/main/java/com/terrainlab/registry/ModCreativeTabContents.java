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
            // Wood sets, in vanilla's per-wood order.
            event.accept(ModItems.PINE_LOG);
            event.accept(ModItems.PINE_WOOD);
            event.accept(ModItems.STRIPPED_PINE_LOG);
            event.accept(ModItems.STRIPPED_PINE_WOOD);
            event.accept(ModItems.PINE_PLANKS);
            event.accept(ModItems.PINE_STAIRS);
            event.accept(ModItems.PINE_SLAB);
            event.accept(ModItems.PINE_FENCE);
            event.accept(ModItems.PINE_FENCE_GATE);
            event.accept(ModItems.PINE_DOOR);
            event.accept(ModItems.PINE_TRAPDOOR);
            event.accept(ModItems.PINE_PRESSURE_PLATE);
            event.accept(ModItems.PINE_BUTTON);
            event.accept(ModItems.AZALEA_LOG);
            event.accept(ModItems.AZALEA_WOOD);
            event.accept(ModItems.STRIPPED_AZALEA_LOG);
            event.accept(ModItems.STRIPPED_AZALEA_WOOD);
            event.accept(ModItems.AZALEA_PLANKS);
            event.accept(ModItems.AZALEA_STAIRS);
            event.accept(ModItems.AZALEA_SLAB);
            event.accept(ModItems.AZALEA_FENCE);
            event.accept(ModItems.AZALEA_FENCE_GATE);
            event.accept(ModItems.AZALEA_DOOR);
            event.accept(ModItems.AZALEA_TRAPDOOR);
            event.accept(ModItems.AZALEA_PRESSURE_PLATE);
            event.accept(ModItems.AZALEA_BUTTON);
            event.accept(ModItems.ENDERWOOD);
            event.accept(ModItems.ENDERWOOD_STAIRS);
            event.accept(ModItems.ENDERWOOD_SLAB);
            event.accept(ModItems.ENDERWOOD_FENCE);
            event.accept(ModItems.ENDERWOOD_FENCE_GATE);
            event.accept(ModItems.ENDERWOOD_DOOR);
            event.accept(ModItems.ENDERWOOD_TRAPDOOR);
            event.accept(ModItems.ENDERWOOD_PRESSURE_PLATE);
            event.accept(ModItems.ENDERWOOD_BUTTON);

        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModItems.PINE_LOG);
            event.accept(ModItems.AZALEA_LOG);
            event.accept(ModItems.PINE_LEAVES);
            event.accept(ModItems.PINE_SAPLING);
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
            event.accept(ModItems.PINE_SIGN);
            event.accept(ModItems.PINE_HANGING_SIGN);
            event.accept(ModItems.AZALEA_SIGN);
            event.accept(ModItems.AZALEA_HANGING_SIGN);
            event.accept(ModItems.ENDERWOOD_SIGN);
            event.accept(ModItems.ENDERWOOD_HANGING_SIGN);
            event.accept(ModItems.LARGE_ITEM_FRAME);
            event.accept(ModItems.EXTRA_LARGE_ITEM_FRAME);
            for (CelestialVariant variant : CelestialVariant.values()) {
                if (variant.usesVanillaConduit()) continue; // vanilla Conduit is already in this tab
                event.accept(ModItems.CELESTIAL_CONDUIT_ITEMS.get(variant));
            }

        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.HOWDAH);
            event.accept(ModItems.HOUSEBOAT_ITEM);
            event.accept(ModItems.PINE_BOAT);
            event.accept(ModItems.PINE_CHEST_BOAT);
            event.accept(ModItems.AZALEA_BOAT);
            event.accept(ModItems.AZALEA_CHEST_BOAT);
            event.accept(ModItems.ENDERWOOD_BOAT);
            event.accept(ModItems.ENDERWOOD_CHEST_BOAT);
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
