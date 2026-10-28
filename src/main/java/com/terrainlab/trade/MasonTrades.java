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
 * Replaces the vanilla Mason trade table with building-block trades; the Novice local stone
 * varies by villager biome type.
 */
public final class MasonTrades {

    private MasonTrades() {}

    private static final Map<ResourceKey<VillagerType>, Item> LOCAL_STONE = Map.of(
            VillagerType.PLAINS, Items.CLAY,
            VillagerType.SAVANNA, Items.TERRACOTTA,
            VillagerType.DESERT, Items.SANDSTONE,
            VillagerType.TAIGA, Items.MOSSY_COBBLESTONE,
            VillagerType.SNOW, Items.PACKED_ICE,
            VillagerType.SWAMP, Items.PACKED_MUD,
            VillagerType.JUNGLE, Items.DRIPSTONE_BLOCK
    );
    private static final Item LOCAL_STONE_FALLBACK = Items.CLAY;

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.MASON) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Local Stone (villager type), Stone - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.VillagerTypeVariantTradeListing.buying(LOCAL_STONE, LOCAL_STONE_FALLBACK, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.VillagerTypeVariantTradeListing.selling(LOCAL_STONE, LOCAL_STONE_FALLBACK, 14, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.STONE, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.STONE, 14, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Deepslate, Andesite - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.DEEPSLATE, 18, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.DEEPSLATE, 14, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.ANDESITE, 18, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.ANDESITE, 14, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Tuff, Diorite - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.TUFF, 18, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.TUFF, 14, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.DIORITE, 18, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.DIORITE, 14, 1, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Granite, Basalt - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.GRANITE, 9, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.GRANITE, 7, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.BASALT, 9, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.BASALT, 7, 1, EXPERT_MAX_USES, 10));

        // Master (level 5): Diamond. XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND, 1, 1, MASTER_MAX_USES, 0));
        master.add(TradeListings.SimpleTradeListing.selling(Items.DIAMOND, 1, 3, MASTER_MAX_USES, 0));
    }
}
