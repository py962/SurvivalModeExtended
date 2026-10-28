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

/**
 * Replaces the vanilla Fisherman trade table. The Master tier sells a fishing rod with
 * biome-specific enchantments at fixed levels.
 */
public final class FishermanTrades {

    private FishermanTrades() {}

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }

    // Fishing rod enchantments per villager type. All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> FISHING_ROD_ENCHANTMENTS_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.LURE, 3)),
            VillagerType.SAVANNA, List.of(fixed(Enchantments.LUCK_OF_THE_SEA, 3)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.LUCK_OF_THE_SEA, 2), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.DESERT, List.of(fixed(Enchantments.LURE, 2), fixed(Enchantments.LUCK_OF_THE_SEA, 2)),
            VillagerType.SNOW, List.of(fixed(Enchantments.LURE, 2), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.LURE, 2), fixed(Enchantments.LUCK_OF_THE_SEA, 2)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.UNBREAKING, 3))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> FISHING_ROD_ENCHANTMENTS_FALLBACK =
            List.of(fixed(Enchantments.LURE, 3));

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.FISHERMAN) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Kelp, Bucket of Cod - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.KELP, 9, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.KELP, 7, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.COD_BUCKET, 3, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.COD_BUCKET, 1, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Cooked Cod, Bucket of Salmon - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.COOKED_COD, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.COOKED_COD, 7, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.SALMON_BUCKET, 3, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.SALMON_BUCKET, 1, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Cooked Salmon, Ink Sac - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.COOKED_SALMON, 9, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.COOKED_SALMON, 7, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.INK_SAC, 9, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.INK_SAC, 7, 1, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Resin Clump, Sponge - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.RESIN_CLUMP, 1, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.RESIN_CLUMP, 1, 3, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.SPONGE, 1, 4, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.SPONGE, 1, 6, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Fishing Rod (villager type). The buy side accepts any rod.
        // XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.FISHING_ROD, 1, 5, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.FISHING_ROD, FISHING_ROD_ENCHANTMENTS_BY_TYPE, FISHING_ROD_ENCHANTMENTS_FALLBACK, 11, MASTER_MAX_USES, 0));
    }
}
