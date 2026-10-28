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
 * Replaces the vanilla Armorer trade table. Enchanted iron armour carries a
 * biome-specific enchantment set at fixed levels, the same for every piece.
 */
public final class ArmorerTrades {

    private ArmorerTrades() {}

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }

    // Shared by all four enchanted iron armour sells. All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> ENCHANT_PAIR_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.PROTECTION, 2), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.SAVANNA, List.of(fixed(Enchantments.UNBREAKING, 3)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.BLAST_PROTECTION, 4)),
            VillagerType.DESERT, List.of(fixed(Enchantments.FIRE_PROTECTION, 4)),
            VillagerType.SNOW, List.of(fixed(Enchantments.PROTECTION, 4)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.PROJECTILE_PROTECTION, 4)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.THORNS, 3))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> ENCHANT_PAIR_FALLBACK =
            List.of(fixed(Enchantments.PROTECTION, 2), fixed(Enchantments.UNBREAKING, 2));

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.ARMORER) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Copper Ingot, Coal - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();

        novice.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.COPPER_INGOT,
                        9,
                        1,
                        NOVICE_MAX_USES,
                        1
                )
        );

        novice.add(
                TradeListings.SimpleTradeListing.selling(
                        Items.COPPER_INGOT,
                        7,
                        1,
                        NOVICE_MAX_USES,
                        1
                )
        );

        novice.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.COAL,
                        18,
                        1,
                        NOVICE_MAX_USES,
                        1
                )
        );

        novice.add(
                TradeListings.SimpleTradeListing.selling(
                        Items.COAL,
                        14,
                        1,
                        NOVICE_MAX_USES,
                        1
                )
        );

        // Apprentice (level 2): Gold Ingot, Armadillo Scute - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();

        apprentice.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.GOLD_INGOT,
                        1,
                        1,
                        APPRENTICE_MAX_USES,
                        2
                )
        );

        apprentice.add(
                TradeListings.SimpleTradeListing.selling(
                        Items.GOLD_INGOT,
                        1,
                        1,
                        APPRENTICE_MAX_USES,
                        2
                )
        );

        apprentice.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.ARMADILLO_SCUTE,
                        5,
                        1,
                        APPRENTICE_MAX_USES,
                        2
                )
        );

        apprentice.add(
                TradeListings.SimpleTradeListing.selling(
                        Items.ARMADILLO_SCUTE,
                        3,
                        1,
                        APPRENTICE_MAX_USES,
                        2
                )
        );

        // Journeyman (level 3): Iron Horse Armour, enchanted iron boots
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.IRON_HORSE_ARMOR, 1, 2, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.IRON_HORSE_ARMOR, 1, 8, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.IRON_BOOTS, 1, 3, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.IRON_BOOTS, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 9, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): enchanted iron helmet + leggings
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.IRON_HELMET, 1, 4, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.IRON_HELMET, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 10, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.IRON_LEGGINGS, 1, 5, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.IRON_LEGGINGS, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 11, EXPERT_MAX_USES, 10));

        // Master (level 5): enchanted iron chestplate. XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.IRON_CHESTPLATE, 1, 6, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(Items.IRON_CHESTPLATE, ENCHANT_PAIR_BY_TYPE, ENCHANT_PAIR_FALLBACK, 12, MASTER_MAX_USES, 0));
    }
}
