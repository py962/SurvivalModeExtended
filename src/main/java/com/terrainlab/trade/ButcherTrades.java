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

import com.terrainlab.registry.ModEnchantments;
import com.terrainlab.registry.ModItems;

/**
 * Replaces the vanilla Butcher trade table. Meat trades buy raw and sell cooked;
 * Taiga and Snow villagers trade venison instead of beef. Master sells a Reach II lead.
 */
public final class ButcherTrades {

    private ButcherTrades() {}

    private static final Map<ResourceKey<VillagerType>, Item> RAW_BEEF_OR_VENISON = Map.of(
            VillagerType.PLAINS, Items.BEEF,
            VillagerType.SAVANNA, Items.BEEF,
            VillagerType.DESERT, Items.BEEF,
            VillagerType.TAIGA, ModItems.VENISON.get(),
            VillagerType.SNOW, ModItems.VENISON.get(),
            VillagerType.SWAMP, Items.BEEF,
            VillagerType.JUNGLE, Items.BEEF
    );
    private static final Item RAW_BEEF_OR_VENISON_FALLBACK = Items.BEEF;

    private static final Map<ResourceKey<VillagerType>, Item> COOKED_BEEF_OR_VENISON = Map.of(
            VillagerType.PLAINS, Items.COOKED_BEEF,
            VillagerType.SAVANNA, Items.COOKED_BEEF,
            VillagerType.DESERT, Items.COOKED_BEEF,
            VillagerType.TAIGA, ModItems.COOKED_VENISON.get(),
            VillagerType.SNOW, ModItems.COOKED_VENISON.get(),
            VillagerType.SWAMP, Items.COOKED_BEEF,
            VillagerType.JUNGLE, Items.COOKED_BEEF
    );
    private static final Item COOKED_BEEF_OR_VENISON_FALLBACK = Items.COOKED_BEEF;

    // Not biome-specific: passed with an empty type map so the fallback always applies.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> REACH_LEAD =
            List.of(TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(ModEnchantments.REACH, 2));

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.BUTCHER) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Bones, Chicken (raw buy / cooked sell) - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.BONE, 9, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.BONE, 7, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.CHICKEN, 9, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.COOKED_CHICKEN, 7, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Charcoal, Mutton (raw buy / cooked sell) - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.CHARCOAL, 18, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.CHARCOAL, 14, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.MUTTON, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.COOKED_MUTTON, 7, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Pork (raw buy / cooked sell), Beef/Venison (villager type, raw buy / cooked sell) - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.PORKCHOP, 9, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.COOKED_PORKCHOP, 7, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.buying(RAW_BEEF_OR_VENISON, RAW_BEEF_OR_VENISON_FALLBACK, 7, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.selling(COOKED_BEEF_OR_VENISON, COOKED_BEEF_OR_VENISON_FALLBACK, 5, 1, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Bucket of Milk, Rabbit Stew - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.MILK_BUCKET, 1, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.MILK_BUCKET, 1, 3, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.RABBIT_STEW, 5, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.RABBIT_STEW, 3, 1, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Lead (Reach II). The buy side accepts any enchanted lead.
        // XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.LEAD, 1, 4, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.LEAD, Map.of(), REACH_LEAD, 5, MASTER_MAX_USES, 0));
    }
}
