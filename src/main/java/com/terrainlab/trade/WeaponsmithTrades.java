package com.terrainlab.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
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
 * Replaces the vanilla Weaponsmith trade table. Planks and wood-variant shields follow the
 * villager's biome type, and the enchanted iron weapons share a biome-specific
 * enchantment set (fixed levels).
 */
public final class WeaponsmithTrades {

    private WeaponsmithTrades() {}

    private static final ResourceKey<Enchantment> REACH = ModEnchantments.REACH;
    private static final ResourceKey<Enchantment> CLEAVE = ModEnchantments.CLEAVE;
    private static final ResourceKey<Enchantment> QUICKSTRIKE = ModEnchantments.QUICKSTRIKE;

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }


    // Enchanted Iron Sword (Master). All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> SWORD_ENCHANT_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.UNBREAKING, 3)),
            VillagerType.SAVANNA, List.of(fixed(REACH, 3)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.KNOCKBACK, 2)),
            VillagerType.DESERT, List.of(fixed(QUICKSTRIKE, 4)),
            VillagerType.SNOW, List.of(fixed(Enchantments.SHARPNESS, 5)),
            VillagerType.SWAMP, List.of(fixed(REACH, 2), fixed(QUICKSTRIKE, 2)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.SHARPNESS, 2), fixed(Enchantments.UNBREAKING, 2))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> SWORD_ENCHANT_FALLBACK =
            List.of(fixed(Enchantments.UNBREAKING, 3));

    // Enchanted Iron Spear (Expert). All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> SPEAR_ENCHANT_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.UNBREAKING, 3)),
            VillagerType.SAVANNA, List.of(fixed(REACH, 3)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.KNOCKBACK, 2)),
            VillagerType.DESERT, List.of(fixed(QUICKSTRIKE, 4)),
            VillagerType.SNOW, List.of(fixed(Enchantments.SHARPNESS, 5)),
            VillagerType.SWAMP, List.of(fixed(REACH, 2), fixed(QUICKSTRIKE, 2)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.SHARPNESS, 2), fixed(Enchantments.UNBREAKING, 2))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> SPEAR_ENCHANT_FALLBACK =
            List.of(fixed(Enchantments.UNBREAKING, 3));

    // Enchanted Iron Axe (Expert). All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> AXE_ENCHANT_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.UNBREAKING, 3)),
            VillagerType.SAVANNA, List.of(fixed(REACH, 3)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.KNOCKBACK, 2)),
            VillagerType.DESERT, List.of(fixed(QUICKSTRIKE, 4)),
            VillagerType.SNOW, List.of(fixed(Enchantments.SHARPNESS, 5)),
            VillagerType.SWAMP, List.of(fixed(REACH, 2), fixed(QUICKSTRIKE, 2)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.SHARPNESS, 2), fixed(Enchantments.UNBREAKING, 2))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> AXE_ENCHANT_FALLBACK =
            List.of(fixed(Enchantments.UNBREAKING, 3));

    // Wood Planks: same wood per type as Shield 1.
    private static final Map<ResourceKey<VillagerType>, Item> PLANKS_BY_TYPE = Map.of(
            VillagerType.PLAINS, Items.BIRCH_PLANKS,
            VillagerType.SAVANNA, Items.ACACIA_PLANKS,
            VillagerType.DESERT, Items.OAK_PLANKS,
            VillagerType.TAIGA, Items.SPRUCE_PLANKS,
            VillagerType.SNOW, ModItems.PINE_PLANKS.get(),
            VillagerType.SWAMP, Items.BIRCH_PLANKS,
            VillagerType.JUNGLE, Items.BAMBOO_PLANKS
    );
    private static final Item PLANKS_FALLBACK = Items.BIRCH_PLANKS;

    // Shield 1 (Apprentice). Plains is Birch in 1.21.11; Poplar in 26.3.
    private static final Map<ResourceKey<VillagerType>, Item> SHIELD_1_BY_TYPE = Map.of(
            VillagerType.PLAINS, ModItems.BIRCH_SHIELD.get(),
            VillagerType.SAVANNA, ModItems.ACACIA_SHIELD.get(),
            VillagerType.DESERT, ModItems.OAK_SHIELD.get(),
            VillagerType.TAIGA, ModItems.SPRUCE_SHIELD.get(),
            VillagerType.SNOW, ModItems.PINE_SHIELD.get(),
            VillagerType.SWAMP, ModItems.BIRCH_SHIELD.get(),
            VillagerType.JUNGLE, ModItems.BAMBOO_SHIELD.get()
    );
    private static final Item SHIELD_1_FALLBACK = ModItems.BIRCH_SHIELD.get();

    // Shield 2 (Journeyman).
    private static final Map<ResourceKey<VillagerType>, Item> SHIELD_2_BY_TYPE = Map.of(
            VillagerType.PLAINS, ModItems.CHERRY_SHIELD.get(),
            VillagerType.SAVANNA, ModItems.JUNGLE_SHIELD.get(),
            VillagerType.DESERT, ModItems.AZALEA_SHIELD.get(),
            VillagerType.TAIGA, ModItems.DARK_OAK_SHIELD.get(),
            VillagerType.SNOW, ModItems.PALE_OAK_SHIELD.get(),
            VillagerType.SWAMP, ModItems.MANGROVE_SHIELD.get(),
            VillagerType.JUNGLE, ModItems.JUNGLE_SHIELD.get()
    );
    private static final Item SHIELD_2_FALLBACK = ModItems.CHERRY_SHIELD.get();

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.WEAPONSMITH) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Iron Ingot, Coal - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.IRON_INGOT, 4, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.IRON_INGOT, 2, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.COAL, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.COAL, 14, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Wood Planks (villager type), Shield 1 (villager type) - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.VillagerTypeVariantTradeListing.buying(PLANKS_BY_TYPE, PLANKS_FALLBACK, 18, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.VillagerTypeVariantTradeListing.selling(PLANKS_BY_TYPE, PLANKS_FALLBACK, 14, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.VillagerTypeVariantTradeListing.buying(SHIELD_1_BY_TYPE, SHIELD_1_FALLBACK, 1, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.VillagerTypeVariantTradeListing.selling(SHIELD_1_BY_TYPE, SHIELD_1_FALLBACK, 1, 2, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Shield 2 (villager type), Anvil - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.buying(SHIELD_2_BY_TYPE, SHIELD_2_FALLBACK, 1, 3, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.selling(SHIELD_2_BY_TYPE, SHIELD_2_FALLBACK, 1, 5, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.ANVIL, 1, 7, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.ANVIL, 1, 9, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Enchanted Iron Axe (villager type), Enchanted Iron Spear (villager type) - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.IRON_AXE, 1, 5, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.IRON_AXE, AXE_ENCHANT_BY_TYPE, AXE_ENCHANT_FALLBACK, 11, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.IRON_SPEAR, 1, 6, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.IRON_SPEAR, SPEAR_ENCHANT_BY_TYPE, SPEAR_ENCHANT_FALLBACK, 12, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Iron Sword (villager type). XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.IRON_SWORD, 1, 8, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.IRON_SWORD, SWORD_ENCHANT_BY_TYPE, SWORD_ENCHANT_FALLBACK, 14, MASTER_MAX_USES, 0));
    }
}
