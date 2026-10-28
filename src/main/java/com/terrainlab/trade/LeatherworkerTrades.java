package com.terrainlab.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;
import java.util.Map;

import com.terrainlab.registry.ModItems;
import com.terrainlab.registry.ModEnchantments;

/**
 * Replaces the vanilla Leatherworker trade table. Enchanted boots and saddles share a
 * biome-specific enchantment mapping; the enchanted bundle always carries Overstacking.
 */
public final class LeatherworkerTrades {

    private LeatherworkerTrades() {}

    private static final ResourceKey<Enchantment> OVERSTACK = ModEnchantments.OVERSTACKING;

    private static final ResourceKey<Enchantment> AGILITY = ModEnchantments.AGILITY;

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }

    // Shared by the Expert boots and the Master saddle. All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> BOOTS_ENCHANTMENTS_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.UNBREAKING, 3)),
            VillagerType.SAVANNA, List.of(fixed(Enchantments.DEPTH_STRIDER, 2), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.FEATHER_FALLING, 2), fixed(AGILITY, 2)),
            VillagerType.DESERT, List.of(fixed(AGILITY, 3)),
            VillagerType.SNOW, List.of(fixed(Enchantments.FROST_WALKER, 2)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.DEPTH_STRIDER, 3)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.FEATHER_FALLING, 4))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> BOOTS_ENCHANTMENTS_FALLBACK =
            List.of(fixed(Enchantments.UNBREAKING, 3));

    // Overstacking I for every villager type (and as the fallback).
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> OVERSTACKING_ONLY =
            List.of(fixed(OVERSTACK, 1));
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> BUNDLE_ENCHANTMENTS_BY_TYPE = Map.of(
            VillagerType.PLAINS, OVERSTACKING_ONLY,
            VillagerType.SAVANNA, OVERSTACKING_ONLY,
            VillagerType.TAIGA, OVERSTACKING_ONLY,
            VillagerType.DESERT, OVERSTACKING_ONLY,
            VillagerType.SNOW, OVERSTACKING_ONLY,
            VillagerType.SWAMP, OVERSTACKING_ONLY,
            VillagerType.JUNGLE, OVERSTACKING_ONLY
    );

    private static final Map<ResourceKey<VillagerType>, Item> LOCAL_DYE_SOURCE = Map.of(
            VillagerType.PLAINS, Items.CORNFLOWER,
            VillagerType.SAVANNA, Items.DANDELION,
            VillagerType.DESERT, Items.CACTUS,
            VillagerType.TAIGA, Items.ROSE_BUSH,
            VillagerType.SNOW, Items.AZURE_BLUET,
            VillagerType.SWAMP, Items.LILY_OF_THE_VALLEY,
            VillagerType.JUNGLE, Items.FERN
    );
    private static final Item LOCAL_DYE_SOURCE_FALLBACK = Items.CORNFLOWER;

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LEATHERWORKER) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Leather, Item Frame - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.LEATHER, 7, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.LEATHER, 5, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.ITEM_FRAME, 5, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.ITEM_FRAME, 3, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Rabbit Hide, Leather Flask - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.RABBIT_HIDE, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.RABBIT_HIDE, 7, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(ModItems.LEATHER_FLASK.get(), 3, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(ModItems.LEATHER_FLASK.get(), 1, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Leather Horse Armour, Local Dye Source (villager type) - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.LEATHER_HORSE_ARMOR, 1, 2, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.LEATHER_HORSE_ARMOR, 1, 4, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.buying(LOCAL_DYE_SOURCE, LOCAL_DYE_SOURCE_FALLBACK, 9, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.selling(LOCAL_DYE_SOURCE, LOCAL_DYE_SOURCE_FALLBACK, 7, 1, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Enchanted Bundle (Overstacking), Enchanted Leather Boots (villager type) - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.BUNDLE, 1, 1, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.BUNDLE, BUNDLE_ENCHANTMENTS_BY_TYPE, OVERSTACKING_ONLY, 7, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.LEATHER_BOOTS, 1, 3, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.LEATHER_BOOTS, BOOTS_ENCHANTMENTS_BY_TYPE, BOOTS_ENCHANTMENTS_FALLBACK, 9, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Saddle (boots mapping). XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.SADDLE, 1, 5, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.SADDLE, BOOTS_ENCHANTMENTS_BY_TYPE, BOOTS_ENCHANTMENTS_FALLBACK, 11, MASTER_MAX_USES, 0));
    }
}
