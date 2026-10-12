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
 * Replaces the vanilla Toolsmith trade table. All four enchanted diamond tools carry the
 * same biome-specific enchantment pair.
 */
public final class ToolsmithTrades {

    private ToolsmithTrades() {}

    private static final ResourceKey<Enchantment> REACH = ModEnchantments.REACH;

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance ranged(ResourceKey<Enchantment> enchantment, int minLevel, int maxLevel) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.ranged(enchantment, minLevel, maxLevel);
    }

    // Shared by all four enchanted diamond tool trades.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> ENCHANT_PAIR_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(ranged(Enchantments.EFFICIENCY, 1, 5), ranged(Enchantments.FORTUNE, 1, 3)),
            VillagerType.SAVANNA, List.of(ranged(Enchantments.UNBREAKING, 1, 3), ranged(Enchantments.FORTUNE, 1, 3)),
            VillagerType.DESERT, List.of(fixed(REACH, 1), ranged(Enchantments.FORTUNE, 1, 3)),
            VillagerType.TAIGA, List.of(ranged(Enchantments.EFFICIENCY, 1, 5), ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.SNOW, List.of(fixed(Enchantments.SILK_TOUCH, 1), ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.SILK_TOUCH, 1), fixed(REACH, 1)),
            VillagerType.JUNGLE, List.of(ranged(Enchantments.EFFICIENCY, 1, 5), fixed(Enchantments.SILK_TOUCH, 1))
    );
    // Plains default.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> ENCHANT_PAIR_FALLBACK =
            List.of(ranged(Enchantments.EFFICIENCY, 1, 5), ranged(Enchantments.FORTUNE, 1, 3));

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

        // Apprentice (level 2): Bucket of Lava, Diamond - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.LAVA_BUCKET, 3, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.LAVA_BUCKET, 1, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND, 1, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.DIAMOND, 1, 3, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Bell, Enchanted Diamond Hoe (villager type) - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.BELL, 1, 7, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.BELL, 1, 9, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND_HOE, 1, 3, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.DIAMOND_HOE, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 9, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Enchanted Diamond Spade (villager type), Enchanted Diamond Axe (villager type) - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND_SHOVEL, 1, 4, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.DIAMOND_SHOVEL, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 10, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND_AXE, 1, 10, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.DIAMOND_AXE, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 16, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Diamond Pickaxe (villager type). XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND_PICKAXE, 1, 14, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.DIAMOND_PICKAXE, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 20, MASTER_MAX_USES, 0));
    }
}
