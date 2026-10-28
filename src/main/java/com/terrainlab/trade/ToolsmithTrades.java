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

import com.terrainlab.registry.ModEnchantments;

/**
 * Replaces the vanilla Toolsmith trade table. All four enchanted iron tools carry the
 * same biome-specific enchantment pair.
 */
public final class ToolsmithTrades {

    private ToolsmithTrades() {}

    private static final ResourceKey<Enchantment> REACH = ModEnchantments.REACH;

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }

    // Shared by all four enchanted iron tool trades. All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> ENCHANT_PAIR_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.EFFICIENCY, 2), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.SAVANNA, List.of(fixed(REACH, 3)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.EFFICIENCY, 5)),
            VillagerType.DESERT, List.of(fixed(Enchantments.SILK_TOUCH, 1)),
            VillagerType.SNOW, List.of(fixed(Enchantments.UNBREAKING, 3)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.AQUA_AFFINITY, 1)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.EFFICIENCY, 2), fixed(REACH, 2))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> ENCHANT_PAIR_FALLBACK =
            List.of(fixed(Enchantments.EFFICIENCY, 2), fixed(Enchantments.UNBREAKING, 2));

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.TOOLSMITH) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Iron Ingot, Stick - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.IRON_INGOT, 4, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.IRON_INGOT, 2, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.STICK, 37, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.STICK, 27, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Bucket, Bucket of Lava - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.BUCKET, 3, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.BUCKET, 1, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.LAVA_BUCKET, 2, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.LAVA_BUCKET, 1, 2, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Bell, Enchanted Iron Hoe (villager type) - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.BELL, 1, 7, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.BELL, 1, 9, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.IRON_HOE, 1, 2, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.IRON_HOE, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 8, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Enchanted Iron Spade (villager type), Enchanted Iron Axe (villager type) - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.IRON_SHOVEL, 1, 4, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.IRON_SHOVEL, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 10, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.IRON_AXE, 1, 5, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.IRON_AXE, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 11, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Iron Pickaxe (villager type). XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.IRON_PICKAXE, 1, 8, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.IRON_PICKAXE, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 14, MASTER_MAX_USES, 0));
    }
}
