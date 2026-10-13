package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.item.HouseboatItem;
import com.terrainlab.item.CalendarItem;
import com.terrainlab.item.ThermometerItem;
import com.terrainlab.item.LeatherFlaskItem;
import com.terrainlab.item.RangefinderCompassItem;
import com.terrainlab.item.BubbleGaugeItem;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BlockItem;
import com.terrainlab.item.XpBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MobBucketItem;
import com.terrainlab.item.FurCloakItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.terrainlab.item.TieredArrowItem;
import com.terrainlab.entity.TieredArrowEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.entity.EquipmentSlot;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.item.CelestialPearlItem;
import com.terrainlab.item.CelestialAmuletItem;
import com.terrainlab.item.CelestialConduitBlockItem;
import com.terrainlab.item.OminousBannerItem;
import net.minecraft.world.item.Rarity;

/** Item registrations. */
public class ModItems {
    // Burn-time keys for the wood-set items (see the wood set section below). Declared first so
    // they are initialised before the static item fields that use them.
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider>
            COOK_WOOD_BLOCKS = net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_WOOD_BLOCKS,
            COOK_WOOD_SLABS = net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_WOOD_SLABS,
            COOK_WOOD_ITEMS_LARGE = net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_WOOD_ITEMS_LARGE,
            COOK_WOOD_ITEMS_EXTRA_SMALL = net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL,
            COOK_HANGING_SIGNS = net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_HANGING_SIGNS,
            COOK_BOATS = net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_BOATS;


    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(TerrainLab.MODID);

    public static final DeferredItem<BlockItem> VOID_GLASS =
            ITEMS.register("void_glass", id -> new BlockItem(
                    ModBlocks.VOID_GLASS.get(),
                    new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    // Drop from void glass broken without Silk Touch; four craft back into a block.
    public static final DeferredItem<Item> VOID_SHARDS =
            ITEMS.register("void_shards", id -> new Item(
                    new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    // Loot/quest item with no custom behaviour.
    public static final DeferredItem<Item> ANCIENT_ARTEFACT =
            ITEMS.register("ancient_artefact", id ->
                    new Item(new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    // Loot/quest item with no custom behaviour.
    public static final DeferredItem<Item> NETHER_IDOL =
            ITEMS.register("nether_idol", id ->
                    new Item(new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    // Drinkable experience bottle: grants XP on finish and leaves a glass bottle.
    public static final DeferredItem<XpBottleItem> EXPERIENCE_BOTTLE =
            ITEMS.register("experience_bottle", id ->
                    new XpBottleItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(16)
                            .component(net.minecraft.core.component.DataComponents.CONSUMABLE,
                                    net.minecraft.world.item.component.Consumable.builder()
                                            .consumeSeconds(1.6F)
                                            .animation(net.minecraft.world.item.ItemUseAnimation.DRINK)
                                            .sound(net.minecraft.sounds.SoundEvents.GENERIC_DRINK)
                                            .build())
                            .usingConvertsTo(net.minecraft.world.item.Items.GLASS_BOTTLE)));

    // Refillable water flask (20 charges); a missing FLASK_CHARGES component reads as full.
    public static final DeferredItem<LeatherFlaskItem> LEATHER_FLASK =
            ITEMS.register("leather_flask", id ->
                    new LeatherFlaskItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)));

    public static final DeferredItem<Item> HOWDAH =
            ITEMS.register("howdah", id -> new Item(new Item.Properties()
                    .setId(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.ITEM, id))
                    .stacksTo(1)
                    .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.SADDLE)
                            .setEquipSound(SoundEvents.HORSE_SADDLE)
                            .setAsset(ModEquipmentAssets.HOWDAH)
                            .setAllowedEntities(net.minecraft.core.HolderSet.direct(
                                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(
                                            ModEntities.ELEPHANT.get())))
                            .setEquipOnInteract(true)
                            .setCanBeSheared(true)
                            .setShearingSound(SoundEvents.SADDLE_UNEQUIP)
                            .build())));

    public static final DeferredItem<Item> VULTURE_FEATHER =
            ITEMS.register("vulture_feather", id ->
                    new Item(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<Item> GOOSE_FEATHER =
            ITEMS.register("goose_feather", id ->
                    new Item(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<RangefinderCompassItem> RANGEFINDER_COMPASS =
            ITEMS.register("rangefinder_compass", id ->
                    new RangefinderCompassItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)));

    public static final DeferredItem<BubbleGaugeItem> BUBBLE_GAUGE =
            ITEMS.register("bubble_gauge", id ->
                    new BubbleGaugeItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)));

    // Cooked from VENISON.
    public static final DeferredItem<Item> COOKED_VENISON =
            ITEMS.register("cooked_venison", id ->
                    new Item(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .food(new net.minecraft.world.food.FoodProperties.Builder()
                                    .nutrition(8)
                                    .saturationModifier(0.8F)
                                    .build())));

    public static final DeferredItem<TieredArrowItem> VULTURE_TIERED_ARROW =
            ITEMS.register("vulture_tiered_arrow", id ->
                    new TieredArrowItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id)),
                            TieredArrowEntity.FeatherType.VULTURE));

    public static final DeferredItem<TieredArrowItem> GOOSE_TIERED_ARROW =
            ITEMS.register("goose_tiered_arrow", id ->
                    new TieredArrowItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id)),
                            TieredArrowEntity.FeatherType.GOOSE));

    public static final DeferredItem<BlockItem> ENDERVINE =
            ITEMS.register("endervine", id ->
                    new BlockItem(ModBlocks.ENDERVINE_HEAD.get(),
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM,
                                            id))));

    // Potion tier is stamped per stack via ModDataComponents.ARROW_TIER by the fletching table.
    public static final DeferredItem<TieredArrowItem> NORMAL_TIERED_ARROW =
            ITEMS.register("normal_tiered_arrow", id ->
                    new TieredArrowItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id)),
                            TieredArrowEntity.FeatherType.NORMAL));

    public static final DeferredItem<TieredArrowItem> OWL_TIERED_ARROW =
            ITEMS.register("owl_tiered_arrow", id ->
                    new TieredArrowItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id)),
                            TieredArrowEntity.FeatherType.OWL));

    public static final DeferredItem<TieredArrowItem> PARROT_TIERED_ARROW =
            ITEMS.register("parrot_tiered_arrow", id ->
                    new TieredArrowItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id)),
                            TieredArrowEntity.FeatherType.PARROT));

    public static final DeferredItem<BlockItem> ENDERWOOD =
            ITEMS.registerSimpleBlockItem("enderwood", ModBlocks.ENDERWOOD);

    public static final DeferredItem<BlockItem> REPLICATOR =
            ITEMS.registerSimpleBlockItem("replicator", ModBlocks.REPLICATOR);

    public static final DeferredItem<HouseboatItem> HOUSEBOAT_ITEM =
            ITEMS.register("houseboat", id ->
                    new HouseboatItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)));

    // Registry id for the raid-captain ominous banner (trades, loot, /give). Places as a plain
    // white banner; use OminousBannerItem.createStack(...) for the patterned stack.
    public static final DeferredItem<OminousBannerItem> OMINOUS_BANNER =
            ITEMS.register("ominous_banner", id ->
                    new OminousBannerItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(16)
                            .rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<BlockItem> FLETCHING_TABLE_ITEM =
            ITEMS.registerSimpleBlockItem("fletching_table", ModBlocks.FLETCHING_TABLE);

    public static final DeferredItem<BlockItem> SCULK_FLOWER =
            ITEMS.registerSimpleBlockItem("sculk_flower", ModBlocks.SCULK_FLOWER);

    public static final DeferredItem<BlockItem> NIGHTCAP_MUSHROOM =
            ITEMS.registerSimpleBlockItem("nightcap_mushroom", ModBlocks.NIGHTCAP_MUSHROOM);

    public static final DeferredItem<BlockItem> MAGNETITE_ORE =
            ITEMS.registerSimpleBlockItem("magnetite_ore", ModBlocks.MAGNETITE_ORE);

    // REDSTONE_PORTAL has no item; it is placed only by the portal frame logic.
    public static final DeferredItem<BlockItem> FIELD_STONE =
            ITEMS.registerSimpleBlockItem("field_stone", ModBlocks.FIELD_STONE);

    // Crafting/drop material.
    public static final DeferredItem<Item> GHOST_FISH_SCALE =
            ITEMS.register("ghost_fish_scale", id ->
                    new Item(new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    // Dropped by magnetite ore; crafting/portal ingredient with no redstone behaviour.
    public static final DeferredItem<Item> MAGNETITE_DUST =
            ITEMS.register("magnetite_dust", id ->
                    new Item(new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    // Crafting materials.
    public static final DeferredItem<Item> GOAT_FUR =
            ITEMS.register("goat_fur", id ->
                    new Item(new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<Item> CARIBOU_FUR =
            ITEMS.register("caribou_fur", id ->
                    new Item(new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<Item> OWL_FEATHER =
            ITEMS.register("owl_feather", id ->
                    new Item(new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<Item> PARROT_FEATHER =
            ITEMS.register("parrot_feather", id ->
                    new Item(new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    // Raw venison; same nutrition as raw beef.
    public static final DeferredItem<Item> VENISON =
            ITEMS.register("venison", id ->
                    new Item(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .food(new net.minecraft.world.food.FoodProperties.Builder()
                                    .nutrition(3)
                                    .saturationModifier(0.3F)
                                    .build())));

    // Vanilla MobBucketItem; uses the generic fish bucket sound.
    public static final DeferredItem<MobBucketItem> GHOST_FISH_BUCKET =
            ITEMS.register("ghost_fish_bucket", id ->
                    new MobBucketItem(ModEntities.GHOST_FISH.get(), Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH,
                            new Item.Properties()
                                    .stacksTo(1)
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> GHOST_FISH_SPAWN_EGG =
            ITEMS.register("ghost_fish_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.GHOST_FISH.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> FROSTBITTEN_SPAWN_EGG =
            ITEMS.register("frostbitten_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.FROSTBITTEN.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> CARIBOU_SPAWN_EGG =
            ITEMS.register("caribou_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.CARIBOU.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> OWL_SPAWN_EGG =
            ITEMS.register("owl_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.OWL.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> VULTURE_SPAWN_EGG =
            ITEMS.register("vulture_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.VULTURE.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> GOOSE_SPAWN_EGG =
            ITEMS.register("goose_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.GOOSE.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> GRIZZLY_BEAR_SPAWN_EGG =
            ITEMS.register("grizzly_bear_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.GRIZZLY_BEAR.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    // Trail Trader spawn eggs.
    public static final DeferredItem<SpawnEggItem> RANGER_SPAWN_EGG =
            ITEMS.register("ranger_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.RANGER.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> ARCHEOLOGIST_SPAWN_EGG =
            ITEMS.register("archeologist_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.ARCHEOLOGIST.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> MARINER_SPAWN_EGG =
            ITEMS.register("mariner_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.MARINER.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    public static final DeferredItem<SpawnEggItem> PRIEST_SPAWN_EGG =
            ITEMS.register("priest_spawn_egg", id ->
                    new SpawnEggItem(
                            new Item.Properties()
                                    .spawnEgg(ModEntities.PRIEST.get())
                                    .setId(net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id))));

    // Fur cloak, worn in the Curios "body" slot (slot assignment via the curios item tag).
    public static final DeferredItem<FurCloakItem> FUR_CLOAK =
            ITEMS.register("fur_cloak", id ->
                    new FurCloakItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)));

    // Calendar: shows the position in the seasonal year via an 18-frame range_dispatch model.
    public static final DeferredItem<CalendarItem> CALENDAR =
            ITEMS.register("calendar", id ->
                    new CalendarItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)));

    // Thermometer (handheld or framed): discrete-band range_dispatch model via ThermometerReading.
    public static final DeferredItem<ThermometerItem> THERMOMETER =
            ITEMS.register("thermometer", id ->
                    new ThermometerItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)));

    // Atlas: low-detail world overview map, generated lazily and centred where it was first used.
    public static final DeferredItem<com.terrainlab.item.AtlasItem> ATLAS =
            ITEMS.register("atlas", id ->
                    new com.terrainlab.item.AtlasItem(new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1)));

    // Wood shields. Combat behaviour comes from the blocks_attacks component built by
    // ShieldFactory from ShieldDefinitions; ShieldCombatEvents handles the stats the component
    // cannot express and resolves stats via SHIELD_ITEMS.

    public static final DeferredItem<Item> OAK_SHIELD =
            ITEMS.register("oak_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("oak_shield"))));
    public static final DeferredItem<Item> BAMBOO_SHIELD =
            ITEMS.register("bamboo_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("bamboo_shield"))));
    public static final DeferredItem<Item> BIRCH_SHIELD =
            ITEMS.register("birch_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("birch_shield"))));
    public static final DeferredItem<Item> CHERRY_SHIELD =
            ITEMS.register("cherry_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("cherry_shield"))));
    public static final DeferredItem<Item> ACACIA_SHIELD =
            ITEMS.register("acacia_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("acacia_shield"))));
    public static final DeferredItem<Item> AZALEA_SHIELD =
            ITEMS.register("azalea_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("azalea_shield"))));
    public static final DeferredItem<Item> SPRUCE_SHIELD =
            ITEMS.register("spruce_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("spruce_shield"))));
    public static final DeferredItem<Item> PINE_SHIELD =
            ITEMS.register("pine_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("pine_shield"))));
    public static final DeferredItem<Item> PALE_OAK_SHIELD =
            ITEMS.register("pale_oak_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("pale_oak_shield"))));
    public static final DeferredItem<Item> DARK_OAK_SHIELD =
            ITEMS.register("dark_oak_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("dark_oak_shield"))));
    public static final DeferredItem<Item> JUNGLE_SHIELD =
            ITEMS.register("jungle_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("jungle_shield"))));
    public static final DeferredItem<Item> MANGROVE_SHIELD =
            ITEMS.register("mangrove_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("mangrove_shield"))));
    public static final DeferredItem<Item> WARPED_SHIELD =
            ITEMS.register("warped_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("warped_shield"))));
    public static final DeferredItem<Item> CRIMSON_SHIELD =
            ITEMS.register("crimson_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("crimson_shield"))));
    public static final DeferredItem<Item> ENDERWOOD_SHIELD =
            ITEMS.register("enderwood_shield", id ->
                    new net.minecraft.world.item.ShieldItem(com.terrainlab.item.ShieldFactory.buildProperties(
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)),
                            com.terrainlab.item.ShieldDefinitions.byId("enderwood_shield"))));

    /** registry id -> registered item, so ShieldCombatEvents can resolve stats at runtime. */
    public static final java.util.Map<String, DeferredItem<Item>> SHIELD_ITEMS = new java.util.LinkedHashMap<>();
    static {
        SHIELD_ITEMS.put("oak_shield", OAK_SHIELD);
        SHIELD_ITEMS.put("bamboo_shield", BAMBOO_SHIELD);
        SHIELD_ITEMS.put("birch_shield", BIRCH_SHIELD);
        SHIELD_ITEMS.put("cherry_shield", CHERRY_SHIELD);
        SHIELD_ITEMS.put("acacia_shield", ACACIA_SHIELD);
        SHIELD_ITEMS.put("azalea_shield", AZALEA_SHIELD);
        SHIELD_ITEMS.put("spruce_shield", SPRUCE_SHIELD);
        SHIELD_ITEMS.put("pine_shield", PINE_SHIELD);
        SHIELD_ITEMS.put("dark_oak_shield", DARK_OAK_SHIELD);
        SHIELD_ITEMS.put("pale_oak_shield", PALE_OAK_SHIELD);
        SHIELD_ITEMS.put("jungle_shield", JUNGLE_SHIELD);
        SHIELD_ITEMS.put("mangrove_shield", MANGROVE_SHIELD);
        SHIELD_ITEMS.put("warped_shield", WARPED_SHIELD);
        SHIELD_ITEMS.put("crimson_shield", CRIMSON_SHIELD);
        SHIELD_ITEMS.put("enderwood_shield", ENDERWOOD_SHIELD);
    }

    public static final DeferredItem<BlockItem> CELESTIAL_FORGE_ITEM =
            ITEMS.register("celestial_forge", id -> new BlockItem(
                    ModBlocks.CELESTIAL_FORGE.get(),
                    new Item.Properties().setId(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))));

    public static final java.util.Map<CelestialVariant, DeferredItem<CelestialPearlItem>> CELESTIAL_PEARLS =
            new java.util.EnumMap<>(CelestialVariant.class);
    public static final java.util.Map<CelestialVariant, DeferredItem<CelestialAmuletItem>> CELESTIAL_AMULETS =
            new java.util.EnumMap<>(CelestialVariant.class);
    public static final java.util.Map<CelestialVariant, DeferredItem<CelestialConduitBlockItem>> CELESTIAL_CONDUIT_ITEMS =
            new java.util.EnumMap<>(CelestialVariant.class);

    static {
        for (CelestialVariant variant : CelestialVariant.values()) {
            CELESTIAL_PEARLS.put(variant, ITEMS.register(variant.id() + "_pearl", id ->
                    new CelestialPearlItem(variant, new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(2))));

            CELESTIAL_AMULETS.put(variant, ITEMS.register(variant.id() + "_amulet", id ->
                    new CelestialAmuletItem(variant, new Item.Properties()
                            .setId(net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.ITEM, id))
                            .stacksTo(1))));

            // Breath uses vanilla's Items.CONDUIT (see conduitItemFor).
            if (variant.usesVanillaConduit()) continue;
            CELESTIAL_CONDUIT_ITEMS.put(variant, ITEMS.register(variant.id() + "_conduit", id ->
                    new CelestialConduitBlockItem(ModBlocks.conduitFor(variant).get(),
                            new Item.Properties().setId(
                                    net.minecraft.resources.ResourceKey.create(
                                            net.minecraft.core.registries.Registries.ITEM, id)))));
        }
    }

    public static CelestialPearlItem pearlFor(CelestialVariant variant) {
        return CELESTIAL_PEARLS.get(variant).get();
    }

    public static CelestialAmuletItem amuletFor(CelestialVariant variant) {
        return CELESTIAL_AMULETS.get(variant).get();
    }

    /**
     * The placeable conduit item for a variant: vanilla's Items.CONDUIT for BREATH, otherwise the
     * mod's CelestialConduitBlockItem. Prefer this over CELESTIAL_CONDUIT_ITEMS, which has no BREATH entry.
     */
    public static Item conduitItemFor(CelestialVariant variant) {
        return variant.usesVanillaConduit()
                ? net.minecraft.world.item.Items.CONDUIT
                : CELESTIAL_CONDUIT_ITEMS.get(variant).get();
    }

    // Sandstorm sand layers.
    public static final DeferredItem<BlockItem> SAND_LAYER =
            ITEMS.registerSimpleBlockItem("sand_layer", ModBlocks.SAND_LAYER);
    public static final DeferredItem<BlockItem> RED_SAND_LAYER =
            ITEMS.registerSimpleBlockItem("red_sand_layer", ModBlocks.RED_SAND_LAYER);

    // Wood sets (blocks in ModBlocks). Enderwood's planks item is ENDERWOOD above.
    // Furnace fuel is an item component since 26.3 (vanilla Items.java: cookingFuel); the values
    // match what the wooden item tags gave in 1.21.11. Enderwood, like the nether woods, is not
    // fuel (it was in minecraft:non_flammable_wood), except its boats, which burned in 1.21.11.
    // Pine
    public static final DeferredItem<BlockItem> PINE_LOG = woodItem("pine_log", ModBlocks.PINE_LOG, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> PINE_WOOD = woodItem("pine_wood", ModBlocks.PINE_WOOD, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> STRIPPED_PINE_LOG = woodItem("stripped_pine_log", ModBlocks.STRIPPED_PINE_LOG, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> STRIPPED_PINE_WOOD = woodItem("stripped_pine_wood", ModBlocks.STRIPPED_PINE_WOOD, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> PINE_PLANKS = woodItem("pine_planks", ModBlocks.PINE_PLANKS, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> PINE_STAIRS = woodItem("pine_stairs", ModBlocks.PINE_STAIRS, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> PINE_SLAB = woodItem("pine_slab", ModBlocks.PINE_SLAB, COOK_WOOD_SLABS);
    public static final DeferredItem<BlockItem> PINE_FENCE = woodItem("pine_fence", ModBlocks.PINE_FENCE, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> PINE_FENCE_GATE = woodItem("pine_fence_gate", ModBlocks.PINE_FENCE_GATE, COOK_WOOD_BLOCKS);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> PINE_DOOR = doorItem("pine_door", ModBlocks.PINE_DOOR, COOK_WOOD_ITEMS_LARGE);
    public static final DeferredItem<BlockItem> PINE_TRAPDOOR = woodItem("pine_trapdoor", ModBlocks.PINE_TRAPDOOR, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> PINE_BUTTON = woodItem("pine_button", ModBlocks.PINE_BUTTON, COOK_WOOD_ITEMS_EXTRA_SMALL);
    public static final DeferredItem<BlockItem> PINE_PRESSURE_PLATE = woodItem("pine_pressure_plate", ModBlocks.PINE_PRESSURE_PLATE, COOK_WOOD_BLOCKS);
    public static final DeferredItem<net.minecraft.world.item.StandingAndWallBlockItem> PINE_SIGN = signItem("pine_sign", ModBlocks.PINE_SIGN, ModBlocks.PINE_WALL_SIGN, COOK_WOOD_ITEMS_LARGE);
    public static final DeferredItem<net.minecraft.world.item.HangingSignItem> PINE_HANGING_SIGN = hangingSignItem("pine_hanging_sign", ModBlocks.PINE_HANGING_SIGN, ModBlocks.PINE_WALL_HANGING_SIGN, COOK_HANGING_SIGNS);
    // Leaves and sapling compost like vanilla's (30% chance); the sapling burns like vanilla saplings.
    public static final DeferredItem<BlockItem> PINE_LEAVES = ITEMS.registerSimpleBlockItem("pine_leaves", ModBlocks.PINE_LEAVES,
            p -> p.compostable(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_LOW));
    public static final DeferredItem<BlockItem> PINE_SAPLING = ITEMS.registerSimpleBlockItem("pine_sapling", ModBlocks.PINE_SAPLING,
            p -> p.compostable(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_LOW)
                    .cookingFuel(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COOKING_TIME_DRY_PLANTS));
    public static final DeferredItem<net.minecraft.world.item.BoatItem> PINE_BOAT = boatItem("pine_boat", ModEntities.PINE_BOAT);
    public static final DeferredItem<net.minecraft.world.item.BoatItem> PINE_CHEST_BOAT = boatItem("pine_chest_boat", ModEntities.PINE_CHEST_BOAT);

    // Azalea
    public static final DeferredItem<BlockItem> AZALEA_LOG = woodItem("azalea_log", ModBlocks.AZALEA_LOG, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> AZALEA_WOOD = woodItem("azalea_wood", ModBlocks.AZALEA_WOOD, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> STRIPPED_AZALEA_LOG = woodItem("stripped_azalea_log", ModBlocks.STRIPPED_AZALEA_LOG, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> STRIPPED_AZALEA_WOOD = woodItem("stripped_azalea_wood", ModBlocks.STRIPPED_AZALEA_WOOD, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> AZALEA_PLANKS = woodItem("azalea_planks", ModBlocks.AZALEA_PLANKS, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> AZALEA_STAIRS = woodItem("azalea_stairs", ModBlocks.AZALEA_STAIRS, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> AZALEA_SLAB = woodItem("azalea_slab", ModBlocks.AZALEA_SLAB, COOK_WOOD_SLABS);
    public static final DeferredItem<BlockItem> AZALEA_FENCE = woodItem("azalea_fence", ModBlocks.AZALEA_FENCE, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> AZALEA_FENCE_GATE = woodItem("azalea_fence_gate", ModBlocks.AZALEA_FENCE_GATE, COOK_WOOD_BLOCKS);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> AZALEA_DOOR = doorItem("azalea_door", ModBlocks.AZALEA_DOOR, COOK_WOOD_ITEMS_LARGE);
    public static final DeferredItem<BlockItem> AZALEA_TRAPDOOR = woodItem("azalea_trapdoor", ModBlocks.AZALEA_TRAPDOOR, COOK_WOOD_BLOCKS);
    public static final DeferredItem<BlockItem> AZALEA_BUTTON = woodItem("azalea_button", ModBlocks.AZALEA_BUTTON, COOK_WOOD_ITEMS_EXTRA_SMALL);
    public static final DeferredItem<BlockItem> AZALEA_PRESSURE_PLATE = woodItem("azalea_pressure_plate", ModBlocks.AZALEA_PRESSURE_PLATE, COOK_WOOD_BLOCKS);
    public static final DeferredItem<net.minecraft.world.item.StandingAndWallBlockItem> AZALEA_SIGN = signItem("azalea_sign", ModBlocks.AZALEA_SIGN, ModBlocks.AZALEA_WALL_SIGN, COOK_WOOD_ITEMS_LARGE);
    public static final DeferredItem<net.minecraft.world.item.HangingSignItem> AZALEA_HANGING_SIGN = hangingSignItem("azalea_hanging_sign", ModBlocks.AZALEA_HANGING_SIGN, ModBlocks.AZALEA_WALL_HANGING_SIGN, COOK_HANGING_SIGNS);
    public static final DeferredItem<net.minecraft.world.item.BoatItem> AZALEA_BOAT = boatItem("azalea_boat", ModEntities.AZALEA_BOAT);
    public static final DeferredItem<net.minecraft.world.item.BoatItem> AZALEA_CHEST_BOAT = boatItem("azalea_chest_boat", ModEntities.AZALEA_CHEST_BOAT);

    // Enderwood (not fuel, apart from the boats)
    public static final DeferredItem<BlockItem> ENDERWOOD_STAIRS = ITEMS.registerSimpleBlockItem("enderwood_stairs", ModBlocks.ENDERWOOD_STAIRS);
    public static final DeferredItem<BlockItem> ENDERWOOD_SLAB = ITEMS.registerSimpleBlockItem("enderwood_slab", ModBlocks.ENDERWOOD_SLAB);
    public static final DeferredItem<BlockItem> ENDERWOOD_FENCE = ITEMS.registerSimpleBlockItem("enderwood_fence", ModBlocks.ENDERWOOD_FENCE);
    public static final DeferredItem<BlockItem> ENDERWOOD_FENCE_GATE = ITEMS.registerSimpleBlockItem("enderwood_fence_gate", ModBlocks.ENDERWOOD_FENCE_GATE);
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> ENDERWOOD_DOOR = doorItem("enderwood_door", ModBlocks.ENDERWOOD_DOOR, null);
    public static final DeferredItem<BlockItem> ENDERWOOD_TRAPDOOR = ITEMS.registerSimpleBlockItem("enderwood_trapdoor", ModBlocks.ENDERWOOD_TRAPDOOR);
    public static final DeferredItem<BlockItem> ENDERWOOD_BUTTON = ITEMS.registerSimpleBlockItem("enderwood_button", ModBlocks.ENDERWOOD_BUTTON);
    public static final DeferredItem<BlockItem> ENDERWOOD_PRESSURE_PLATE = ITEMS.registerSimpleBlockItem("enderwood_pressure_plate", ModBlocks.ENDERWOOD_PRESSURE_PLATE);
    public static final DeferredItem<net.minecraft.world.item.StandingAndWallBlockItem> ENDERWOOD_SIGN = signItem("enderwood_sign", ModBlocks.ENDERWOOD_SIGN, ModBlocks.ENDERWOOD_WALL_SIGN, null);
    public static final DeferredItem<net.minecraft.world.item.HangingSignItem> ENDERWOOD_HANGING_SIGN = hangingSignItem("enderwood_hanging_sign", ModBlocks.ENDERWOOD_HANGING_SIGN, ModBlocks.ENDERWOOD_WALL_HANGING_SIGN, null);
    public static final DeferredItem<net.minecraft.world.item.BoatItem> ENDERWOOD_BOAT = boatItem("enderwood_boat", ModEntities.ENDERWOOD_BOAT);
    public static final DeferredItem<net.minecraft.world.item.BoatItem> ENDERWOOD_CHEST_BOAT = boatItem("enderwood_chest_boat", ModEntities.ENDERWOOD_CHEST_BOAT);

    // Item helpers mirroring vanilla 26.3 Items.registerBlock / Items.java wood entries
    // (block description prefix, signs: StandingAndWallBlockItem, stack size 16, sign text components).
    private static DeferredItem<BlockItem> woodItem(String name,
            java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> block,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider> fuel) {
        return ITEMS.registerSimpleBlockItem(name, block, p -> p.cookingFuel(fuel));
    }

    private static Item.Properties fuel(Item.Properties p,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider> fuel) {
        return fuel == null ? p : p.cookingFuel(fuel);
    }

    private static DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> doorItem(
            String name, java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> door,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider> fuelTime) {
        return ITEMS.registerItem(name,
                p -> new net.minecraft.world.item.DoubleHighBlockItem(door.get(), p),
                p -> fuel(p.useBlockDescriptionPrefix(), fuelTime));
    }

    private static DeferredItem<net.minecraft.world.item.StandingAndWallBlockItem> signItem(String name,
            java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> standing,
            java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> wall,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider> fuelTime) {
        return ITEMS.registerItem(name,
                p -> new net.minecraft.world.item.StandingAndWallBlockItem(standing.get(), wall.get(), net.minecraft.core.Direction.DOWN, p),
                p -> fuel(p.stacksTo(16).signText().useBlockDescriptionPrefix(), fuelTime));
    }

    private static DeferredItem<net.minecraft.world.item.HangingSignItem> hangingSignItem(String name,
            java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> ceiling,
            java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> wall,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider> fuelTime) {
        return ITEMS.registerItem(name,
                p -> new net.minecraft.world.item.HangingSignItem(ceiling.get(), wall.get(), p),
                p -> fuel(p.stacksTo(16).signText().useBlockDescriptionPrefix(), fuelTime));
    }

    private static DeferredItem<net.minecraft.world.item.BoatItem> boatItem(String name,
            java.util.function.Supplier<? extends net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.vehicle.boat.AbstractBoat>> type) {
        return ITEMS.registerItem(name,
                p -> new net.minecraft.world.item.BoatItem(type.get(), p),
                p -> p.stacksTo(1).cookingFuel(COOK_BOATS));
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
