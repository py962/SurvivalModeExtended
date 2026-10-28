package com.terrainlab.registry;

import com.terrainlab.celestial.CelestialVariant;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Adds TerrainLab items to the matching vanilla creative tabs (no dedicated
 * mod tab), each inserted right next to its closest vanilla relative.
 * <p>
 * Every anchor must be a plain vanilla item that vanilla always lists in that tab (parent and
 * search): NeoForge's insertAfter/insertBefore throw if the anchor is missing.
 */
public class ModCreativeTabContents {

    private static final CreativeModeTab.TabVisibility VISIBILITY = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;

    public static void register(IEventBus eventBus) {
        eventBus.addListener(ModCreativeTabContents::buildContents);
    }

    /** Inserts {@code items}, in order, as a contiguous run straight after {@code anchor}. */
    private static void after(BuildCreativeModeTabContentsEvent event, ItemLike anchor, ItemLike... items) {
        ItemStack previous = new ItemStack(anchor);
        for (ItemLike item : items) {
            ItemStack stack = new ItemStack(item);
            event.insertAfter(previous, stack, VISIBILITY);
            previous = stack;
        }
    }

    /** Inserts {@code items}, in order, as a contiguous run straight before {@code anchor}. */
    private static void before(BuildCreativeModeTabContentsEvent event, ItemLike anchor, ItemLike... items) {
        ItemStack target = new ItemStack(anchor);
        for (ItemLike item : items) {
            event.insertBefore(target, new ItemStack(item), VISIBILITY);
        }
    }

    private static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            // Wood sets in vanilla's per-wood order. Pine follows its relative spruce; Azalea (no vanilla
            // relative) joins the overworld woods after the last one, Pale Oak, matching where vanilla puts
            // azalea leaves; Enderwood (planks only) follows the nether woods, keeping vanilla's
            // overworld -> nether -> end order.
            after(event, Items.SPRUCE_BUTTON,
                    ModItems.PINE_LOG, ModItems.PINE_WOOD, ModItems.STRIPPED_PINE_LOG, ModItems.STRIPPED_PINE_WOOD,
                    ModItems.PINE_PLANKS, ModItems.PINE_STAIRS, ModItems.PINE_SLAB, ModItems.PINE_FENCE,
                    ModItems.PINE_FENCE_GATE, ModItems.PINE_DOOR, ModItems.PINE_TRAPDOOR,
                    ModItems.PINE_PRESSURE_PLATE, ModItems.PINE_BUTTON);
            after(event, Items.PALE_OAK_BUTTON,
                    ModItems.AZALEA_LOG, ModItems.AZALEA_WOOD, ModItems.STRIPPED_AZALEA_LOG, ModItems.STRIPPED_AZALEA_WOOD,
                    ModItems.AZALEA_PLANKS, ModItems.AZALEA_STAIRS, ModItems.AZALEA_SLAB, ModItems.AZALEA_FENCE,
                    ModItems.AZALEA_FENCE_GATE, ModItems.AZALEA_DOOR, ModItems.AZALEA_TRAPDOOR,
                    ModItems.AZALEA_PRESSURE_PLATE, ModItems.AZALEA_BUTTON);
            after(event, Items.WARPED_BUTTON,
                    ModItems.ENDERWOOD, ModItems.ENDERWOOD_STAIRS, ModItems.ENDERWOOD_SLAB, ModItems.ENDERWOOD_FENCE,
                    ModItems.ENDERWOOD_FENCE_GATE, ModItems.ENDERWOOD_DOOR, ModItems.ENDERWOOD_TRAPDOOR,
                    ModItems.ENDERWOOD_PRESSURE_PLATE, ModItems.ENDERWOOD_BUTTON);

        } else if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            // Sand layers after their sand, like vanilla's snow layer after the snow block.
            after(event, Items.SAND, ModItems.SAND_LAYER);
            after(event, Items.RED_SAND, ModItems.RED_SAND_LAYER);
            after(event, Items.END_STONE, ModItems.VOID_GLASS);
            // Magnetite ore is a redstone-ore look-alike.
            after(event, Items.DEEPSLATE_REDSTONE_ORE, ModItems.MAGNETITE_ORE);
            after(event, Items.SPRUCE_LOG, ModItems.PINE_LOG);
            after(event, Items.PALE_OAK_LOG, ModItems.AZALEA_LOG);
            after(event, Items.SPRUCE_LEAVES, ModItems.PINE_LEAVES);
            after(event, Items.SPRUCE_SAPLING, ModItems.PINE_SAPLING);
            after(event, Items.RED_MUSHROOM, ModItems.NIGHTCAP_MUSHROOM);
            after(event, Items.CHORUS_FLOWER, ModItems.ENDERVINE);
            after(event, Items.SCULK_VEIN, ModItems.SCULK_FLOWER);

        } else if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            // Field stone is the redstone-powered portal frame.
            after(event, Items.REDSTONE_BLOCK, ModItems.FIELD_STONE);
            // The replicator runs one batch per redstone pulse, like the crafter.
            after(event, Items.CRAFTER, ModItems.REPLICATOR);
            after(event, Items.HOPPER_MINECART, ModItems.DISPENSER_MINECART, ModItems.DROPPER_MINECART);

        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            // The mod's Ominous Banner is already here: vanilla's own slot builds it (RaidOminousBannerMixin).
            // Vanilla's fletching table is replaced by the mod's (uncraftable, not a job site, swapped
            // out of structures): the mod's table takes its slot and vanilla's is removed.
            after(event, Items.FLETCHING_TABLE, ModItems.FLETCHING_TABLE_ITEM);
            event.remove(new ItemStack(Items.FLETCHING_TABLE), VISIBILITY);
            // The replicator is furnace-like (blocks in, fuel, doubled blocks out).
            after(event, Items.BLAST_FURNACE, ModItems.REPLICATOR);
            after(event, Items.ENCHANTING_TABLE, ModItems.CELESTIAL_FORGE_ITEM);
            // Vanilla's Conduit is the Conduit of Breath (the first variant): it keeps its vanilla slot
            // and the other seven celestial conduits follow it in variant order.
            ItemLike previous = Items.CONDUIT;
            for (CelestialVariant variant : CelestialVariant.values()) {
                if (variant.usesVanillaConduit()) continue;
                Item conduit = ModItems.conduitItemFor(variant);
                after(event, previous, conduit);
                previous = conduit;
            }
            after(event, Items.GLOW_ITEM_FRAME, ModItems.LARGE_ITEM_FRAME, ModItems.EXTRA_LARGE_ITEM_FRAME);
            after(event, Items.SPRUCE_HANGING_SIGN, ModItems.PINE_SIGN, ModItems.PINE_HANGING_SIGN);
            after(event, Items.PALE_OAK_HANGING_SIGN, ModItems.AZALEA_SIGN, ModItems.AZALEA_HANGING_SIGN);
            after(event, Items.WARPED_HANGING_SIGN, ModItems.ENDERWOOD_SIGN, ModItems.ENDERWOOD_HANGING_SIGN);

        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            after(event, Items.PUFFERFISH_BUCKET, ModItems.GHOST_FISH_BUCKET);
            // Instruments with the compass and clock; the atlas with the map.
            after(event, Items.RECOVERY_COMPASS, ModItems.RANGEFINDER_COMPASS);
            after(event, Items.CLOCK, ModItems.CALENDAR, ModItems.THERMOMETER, ModItems.BUBBLE_GAUGE);
            after(event, Items.MAP, ModItems.ATLAS);
            // The howdah is the elephant's saddle.
            after(event, Items.SADDLE, ModItems.HOWDAH);
            after(event, Items.SPRUCE_CHEST_BOAT, ModItems.PINE_BOAT, ModItems.PINE_CHEST_BOAT);
            after(event, Items.PALE_OAK_CHEST_BOAT, ModItems.AZALEA_BOAT, ModItems.AZALEA_CHEST_BOAT);
            after(event, Items.BAMBOO_CHEST_RAFT,
                    ModItems.ENDERWOOD_BOAT, ModItems.ENDERWOOD_CHEST_BOAT, ModItems.HOUSEBOAT_ITEM);
            after(event, Items.HOPPER_MINECART, ModItems.DISPENSER_MINECART, ModItems.DROPPER_MINECART);

        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            // Vanilla's Shield is replaced by the mod's Oak Shield (its recipe is disabled): the wood
            // shields take its slot and vanilla's is removed.
            after(event, Items.SHIELD, ModItems.SHIELD_ITEMS.values().toArray(new DeferredItem<?>[0]));
            event.remove(new ItemStack(Items.SHIELD), VISIBILITY);
            after(event, Items.LEATHER_BOOTS, ModItems.FUR_CLOAK);
            ItemLike[] amulets = new ItemLike[CelestialVariant.values().length];
            for (CelestialVariant variant : CelestialVariant.values()) {
                amulets[variant.ordinal()] = ModItems.CELESTIAL_AMULETS.get(variant);
            }
            after(event, Items.TOTEM_OF_UNDYING, amulets);
            // Ahead of the long run of tipped arrows, so the tiered arrows stay in view.
            after(event, Items.SPECTRAL_ARROW,
                    ModItems.NORMAL_TIERED_ARROW, ModItems.OWL_TIERED_ARROW, ModItems.PARROT_TIERED_ARROW,
                    ModItems.VULTURE_TIERED_ARROW, ModItems.GOOSE_TIERED_ARROW);

        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            after(event, Items.COOKED_BEEF, ModItems.VENISON, ModItems.COOKED_VENISON);
            after(event, Items.HONEY_BOTTLE, ModItems.LEATHER_FLASK, ModItems.EXPERIENCE_BOTTLE);

        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            after(event, Items.FEATHER,
                    ModItems.OWL_FEATHER, ModItems.PARROT_FEATHER, ModItems.VULTURE_FEATHER, ModItems.GOOSE_FEATHER);
            after(event, Items.RABBIT_HIDE, ModItems.GOAT_FUR, ModItems.CARIBOU_FUR);
            after(event, Items.ARMADILLO_SCUTE, ModItems.GHOST_FISH_SCALE);
            ItemLike[] pearls = new ItemLike[CelestialVariant.values().length];
            for (CelestialVariant variant : CelestialVariant.values()) {
                pearls[variant.ordinal()] = ModItems.CELESTIAL_PEARLS.get(variant);
            }
            after(event, Items.ENDER_EYE, pearls);
            after(event, Items.POPPED_CHORUS_FRUIT, ModItems.VOID_SHARDS);
            after(event, Items.REDSTONE, ModItems.MAGNETITE_DUST);
            // Relics next to the archaeology finds.
            after(event, Items.SNORT_POTTERY_SHERD, ModItems.ANCIENT_ARTEFACT, ModItems.NETHER_IDOL);
            after(event, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, ModItems.DIAMOND_UPGRADE_SMITHING_TEMPLATE);

        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            // Vanilla groups eggs by kind and sorts alphabetically within each group; each mod egg goes
            // into its group at its alphabetical place.
            after(event, Items.BEE_SPAWN_EGG, ModItems.CARIBOU_SPAWN_EGG);
            after(event, Items.GOAT_SPAWN_EGG, ModItems.GOOSE_SPAWN_EGG, ModItems.GRIZZLY_BEAR_SPAWN_EGG);
            after(event, Items.OCELOT_SPAWN_EGG, ModItems.OWL_SPAWN_EGG);
            after(event, Items.RABBIT_SPAWN_EGG, ModItems.VULTURE_SPAWN_EGG);
            after(event, Items.FROG_SPAWN_EGG, ModItems.GHOST_FISH_SPAWN_EGG);
            before(event, Items.TRADER_LLAMA_SPAWN_EGG,
                    ModItems.ARCHEOLOGIST_SPAWN_EGG, ModItems.MARINER_SPAWN_EGG,
                    ModItems.PRIEST_SPAWN_EGG, ModItems.RANGER_SPAWN_EGG);
            after(event, Items.DROWNED_SPAWN_EGG, ModItems.FROSTBITTEN_SPAWN_EGG);
        }
    }
}
