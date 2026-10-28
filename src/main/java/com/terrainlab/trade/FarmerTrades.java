package com.terrainlab.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;
import java.util.Map;

/**
 * Replaces the vanilla Farmer trade table. Some crop trades vary by villager biome type.
 */
public final class FarmerTrades {

    private FarmerTrades() {}

    private static final Map<ResourceKey<VillagerType>, Item> EXPERT_PRODUCE = Map.of(
            VillagerType.PLAINS, Items.PUMPKIN,
            VillagerType.SAVANNA, Items.MELON_SLICE,
            VillagerType.DESERT, Items.MELON_SLICE,
            VillagerType.TAIGA, Items.APPLE,
            VillagerType.SNOW, Items.PUMPKIN,
            VillagerType.SWAMP, Items.APPLE,
            VillagerType.JUNGLE, Items.MELON_SLICE
    );
    private static final Item EXPERT_PRODUCE_FALLBACK = Items.PUMPKIN;

    // Restock limits shrink with level: common trades restock often, valuable ones are scarcer.
    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.FARMER) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Wheat, Carrot - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.WHEAT, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.WHEAT, 14, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.CARROT, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.CARROT, 14, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Potato, Beetroot - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.POTATO, 18, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.POTATO, 14, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.BEETROOT, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.BEETROOT, 7, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Bread, Brown Mushroom - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.BREAD, 9, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.BREAD, 7, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.BROWN_MUSHROOM, 9, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.BROWN_MUSHROOM, 7, 1, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Red Mushroom, Apple, Melon or Pumpkin (villager type) - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.RED_MUSHROOM, 9, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.RED_MUSHROOM, 7, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.VillagerTypeVariantTradeListing.buying(EXPERT_PRODUCE, EXPERT_PRODUCE_FALLBACK, 5, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.VillagerTypeVariantTradeListing.selling(EXPERT_PRODUCE, EXPERT_PRODUCE_FALLBACK, 3, 1, EXPERT_MAX_USES, 10));

        // Master (level 5): Honey Bottle. XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.HONEY_BOTTLE, 1, 1, MASTER_MAX_USES, 0));
        master.add(TradeListings.SimpleTradeListing.selling(Items.HONEY_BOTTLE, 1, 3, MASTER_MAX_USES, 0));
    }
}
