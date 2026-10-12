package com.terrainlab.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
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
 * Replaces the vanilla Librarian trade table. The Expert and Master tiers each sell an
 * enchanted book whose enchantment depends on the villager's biome type.
 */
public final class LibrarianTrades {

    private LibrarianTrades() {}

    private static final ResourceKey<Enchantment> RECALL = ModEnchantments.RECALL;

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance ranged(ResourceKey<Enchantment> enchantment, int minLevel, int maxLevel) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.ranged(enchantment, minLevel, maxLevel);
    }

    // Enchanted Book 1 (Expert).
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> BOOK_1_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.VANISHING_CURSE, 1)),
            VillagerType.SAVANNA, List.of(fixed(Enchantments.CHANNELING, 1)),
            VillagerType.DESERT, List.of(fixed(Enchantments.BINDING_CURSE, 1)),
            VillagerType.TAIGA, List.of(ranged(Enchantments.FIRE_ASPECT, 1, 2)),
            VillagerType.SNOW, List.of(ranged(Enchantments.RESPIRATION, 1, 3)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.AQUA_AFFINITY, 1)),
            VillagerType.JUNGLE, List.of(ranged(Enchantments.BANE_OF_ARTHROPODS, 1, 5))
    );

    // Enchanted Book 2 (Master).
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> BOOK_2_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(ranged(Enchantments.SMITE, 1, 5)),
            VillagerType.SAVANNA, List.of(ranged(Enchantments.RIPTIDE, 1, 3)),
            VillagerType.DESERT, List.of(ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.TAIGA, List.of(ranged(Enchantments.LOOTING, 1, 3)),
            VillagerType.SNOW, List.of(fixed(Enchantments.FLAME, 1)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.MENDING, 1)),
            VillagerType.JUNGLE, List.of(fixed(RECALL, 1))
    );

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Book, Candle - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.BOOK, 5, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.BOOK, 3, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.CANDLE, 4, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.CANDLE, 2, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Lapis, Thermometer - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.LAPIS_LAZULI, 3, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.LAPIS_LAZULI, 1, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(ModItems.THERMOMETER.get(), 1, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(ModItems.THERMOMETER.get(), 1, 3, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Book and Quill, Clock - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.WRITABLE_BOOK, 3, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.WRITABLE_BOOK, 1, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.CLOCK, 1, 3, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.CLOCK, 1, 5, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Enchanted Book 1 (villager type), Calendar - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.ENCHANTED_BOOK, 1, 4, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedBookSellListing(BOOK_1_BY_TYPE, List.of(), 10, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(ModItems.CALENDAR.get(), 1, 3, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(ModItems.CALENDAR.get(), 1, 5, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Book 2 (villager type). XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        // Buys below the Expert sell price (10), so the villager's own books cannot be flipped for profit.
        master.add(TradeListings.SimpleTradeListing.buying(Items.ENCHANTED_BOOK, 1, 6, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedBookSellListing(BOOK_2_BY_TYPE, List.of(), 12, MASTER_MAX_USES, 0));
    }
}
