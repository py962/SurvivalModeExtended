package com.terrainlab.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

import com.terrainlab.registry.ModItems;

/**
 * Replaces the vanilla Cartographer trade table with navigation items, ending in the Atlas at Master.
 */
public final class CartographerTrades {

    private CartographerTrades() {}

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CARTOGRAPHER) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Paper, Compass - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.PAPER, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.PAPER, 14, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.COMPASS, 3, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.COMPASS, 1, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Glass, Map - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.GLASS, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.GLASS, 7, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.MAP, 3, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.MAP, 1, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Amethyst, Spyglass - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.AMETHYST_SHARD, 3, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.AMETHYST_SHARD, 1, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.SPYGLASS, 1, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.SPYGLASS, 1, 3, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Bubble Gauge, Rangefinder Compass - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(ModItems.BUBBLE_GAUGE.get(), 1, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(ModItems.BUBBLE_GAUGE.get(), 1, 3, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(ModItems.RANGEFINDER_COMPASS.get(), 1, 5, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(ModItems.RANGEFINDER_COMPASS.get(), 1, 7, EXPERT_MAX_USES, 10));

        // Master (level 5): Atlas. XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(ModItems.ATLAS.get(), 1, 5, MASTER_MAX_USES, 0));
        master.add(TradeListings.SimpleTradeListing.selling(ModItems.ATLAS.get(), 1, 7, MASTER_MAX_USES, 0));
    }
}
