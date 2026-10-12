package com.terrainlab.trade;

import com.terrainlab.registry.ModEnchantments;
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
 * Replaces the vanilla Armorer trade table. Enchanted diamond armour carries a
 * biome-specific enchantment pair; the Swamp chestplate is the only source of Reach.
 */
public final class ArmorerTrades {

    private ArmorerTrades() {}

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance ranged(
            ResourceKey<Enchantment> enchantment,
            int minLevel,
            int maxLevel
    ) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.ranged(
                enchantment,
                minLevel,
                maxLevel
        );
    }

    // Enchantment pairs for the enchanted diamond armour sells (all pieces except the Swamp chestplate).
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> ENCHANT_PAIR_BY_TYPE = Map.of(
            VillagerType.PLAINS,
            List.of(
                    ranged(Enchantments.PROTECTION, 1, 4),
                    ranged(Enchantments.THORNS, 1, 3)
            ),

            VillagerType.SAVANNA,
            List.of(
                    ranged(Enchantments.PROTECTION, 1, 4),
                    ranged(Enchantments.UNBREAKING, 1, 3)
            ),

            VillagerType.DESERT,
            List.of(
                    ranged(Enchantments.BLAST_PROTECTION, 1, 4),
                    ranged(Enchantments.UNBREAKING, 1, 3)
            ),

            VillagerType.TAIGA,
            List.of(
                    ranged(Enchantments.BLAST_PROTECTION, 1, 4),
                    ranged(Enchantments.THORNS, 1, 3)
            ),

            VillagerType.SNOW,
            List.of(
                    ranged(Enchantments.FIRE_PROTECTION, 1, 4),
                    ranged(Enchantments.THORNS, 1, 3)
            ),

            VillagerType.SWAMP,
            List.of(
                    ranged(Enchantments.UNBREAKING, 1, 3),
                    ranged(Enchantments.THORNS, 1, 3)
            ),

            VillagerType.JUNGLE,
            List.of(
                    ranged(Enchantments.PROJECTILE_PROTECTION, 1, 4),
                    ranged(Enchantments.UNBREAKING, 1, 3)
            )
    );

    // Same pairs as the other pieces, except the Swamp chestplate swaps Thorns for Reach.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> CHESTPLATE_ENCHANT_PAIR_BY_TYPE =
            chestplatePairs();

    private static Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> chestplatePairs() {
        var pairs = new java.util.HashMap<>(ENCHANT_PAIR_BY_TYPE);
        pairs.put(VillagerType.SWAMP, List.of(
                ranged(Enchantments.UNBREAKING, 1, 3),
                ranged(ModEnchantments.REACH, 1, 3)
        ));
        return Map.copyOf(pairs);
    }

    // Plains pair.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> ENCHANT_PAIR_FALLBACK =
            List.of(
                    ranged(Enchantments.PROTECTION, 1, 4),
                    ranged(Enchantments.THORNS, 1, 3)
            );

    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> CHESTPLATE_ENCHANT_PAIR_FALLBACK =
            ENCHANT_PAIR_FALLBACK;

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

        // Journeyman (level 3): enchanted diamond boots + helmet
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();

        journeyman.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.DIAMOND_BOOTS,
                        1,
                        6,
                        JOURNEYMAN_MAX_USES,
                        5
                )
        );

        journeyman.add(
                new TradeListings.VillagerTypeEnchantedSellListing(
                        Items.DIAMOND_BOOTS,
                        ENCHANT_PAIR_BY_TYPE,
                        ENCHANT_PAIR_FALLBACK,
                        12,
                        JOURNEYMAN_MAX_USES,
                        5
                )
        );

        journeyman.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.DIAMOND_HELMET,
                        1,
                        6,
                        JOURNEYMAN_MAX_USES,
                        5
                )
        );

        journeyman.add(
                new TradeListings.VillagerTypeEnchantedSellListing(
                        Items.DIAMOND_HELMET,
                        ENCHANT_PAIR_BY_TYPE,
                        ENCHANT_PAIR_FALLBACK,
                        12,
                        JOURNEYMAN_MAX_USES,
                        5
                )
        );

        // Expert (level 4): enchanted diamond leggings + chestplate
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();

        expert.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.DIAMOND_LEGGINGS,
                        1,
                        10,
                        EXPERT_MAX_USES,
                        10
                )
        );

        expert.add(
                new TradeListings.VillagerTypeEnchantedSellListing(
                        Items.DIAMOND_LEGGINGS,
                        ENCHANT_PAIR_BY_TYPE,
                        ENCHANT_PAIR_FALLBACK,
                        16,
                        EXPERT_MAX_USES,
                        10
                )
        );

        expert.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.DIAMOND_CHESTPLATE,
                        1,
                        10,
                        EXPERT_MAX_USES,
                        10
                )
        );

        // Chestplate uses its own map so Swamp gets Reach.
        expert.add(
                new TradeListings.VillagerTypeEnchantedSellListing(
                        Items.DIAMOND_CHESTPLATE,
                        CHESTPLATE_ENCHANT_PAIR_BY_TYPE,
                        CHESTPLATE_ENCHANT_PAIR_FALLBACK,
                        16,
                        EXPERT_MAX_USES,
                        10
                )
        );

        // Master (level 5): Golden Horse Armour. XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();

        master.add(
                TradeListings.SimpleTradeListing.buying(
                        Items.GOLDEN_HORSE_ARMOR,
                        1,
                        17,
                        MASTER_MAX_USES,
                        0
                )
        );

        master.add(
                TradeListings.SimpleTradeListing.selling(
                        Items.GOLDEN_HORSE_ARMOR,
                        1,
                        23,
                        MASTER_MAX_USES,
                        0
                )
        );
    }
}
