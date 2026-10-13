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
 * villager's biome type, and each enchanted diamond weapon has its own biome-specific
 * enchantment pair limited to enchantments that suit it.
 */
public final class WeaponsmithTrades {

    private WeaponsmithTrades() {}

    private static final ResourceKey<Enchantment> REACH = ModEnchantments.REACH;
    private static final ResourceKey<Enchantment> CLEAVE = ModEnchantments.CLEAVE;

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance ranged(ResourceKey<Enchantment> enchantment, int minLevel, int maxLevel) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.ranged(enchantment, minLevel, maxLevel);
    }

    // Enchanted Diamond Sword: Sweeping Edge on the Taiga/Swamp rows.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> SWORD_ENCHANT_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.KNOCKBACK, 1, 2)),
            VillagerType.SAVANNA, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(REACH, 1, 3)),
            VillagerType.DESERT, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.TAIGA, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.SWEEPING_EDGE, 1, 3)),
            VillagerType.SNOW, List.of(ranged(Enchantments.KNOCKBACK, 1, 2), ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.SWAMP, List.of(ranged(REACH, 1, 3), ranged(Enchantments.SWEEPING_EDGE, 1, 3)),
            VillagerType.JUNGLE, List.of(ranged(REACH, 1, 3), ranged(Enchantments.UNBREAKING, 1, 3))
    );
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> SWORD_ENCHANT_FALLBACK =
            List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.KNOCKBACK, 1, 2));

    // Enchanted Diamond Spear: Lunge on the Taiga/Swamp rows.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> SPEAR_ENCHANT_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.KNOCKBACK, 1, 2)),
            VillagerType.SAVANNA, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(REACH, 1, 3)),
            VillagerType.DESERT, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.TAIGA, List.of(ranged(Enchantments.SHARPNESS, 1, 5), fixed(Enchantments.LUNGE, 1)),
            VillagerType.SNOW, List.of(ranged(Enchantments.KNOCKBACK, 1, 2), ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.SWAMP, List.of(ranged(REACH, 1, 3), fixed(Enchantments.LUNGE, 1)),
            VillagerType.JUNGLE, List.of(ranged (REACH, 1, 3), ranged(Enchantments.UNBREAKING, 1, 3))
    );
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> SPEAR_ENCHANT_FALLBACK =
            List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.KNOCKBACK, 1, 2));

    // Enchanted Diamond Axe: Cleave on the Taiga/Swamp rows.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> AXE_ENCHANT_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.KNOCKBACK, 1, 2)),
            VillagerType.SAVANNA, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(REACH, 1,3)),
            VillagerType.DESERT, List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.TAIGA, List.of(ranged(Enchantments.SHARPNESS, 1, 5),ranged(CLEAVE, 1, 3)),
            VillagerType.SNOW, List.of(ranged(Enchantments.KNOCKBACK, 1, 2), ranged(Enchantments.UNBREAKING, 1, 3)),
            VillagerType.SWAMP, List.of(ranged(REACH, 1, 3), ranged(CLEAVE, 1, 3)),
            VillagerType.JUNGLE, List.of(ranged(REACH, 1,3), ranged(Enchantments.UNBREAKING, 1, 3))
    );
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> AXE_ENCHANT_FALLBACK =
            List.of(ranged(Enchantments.SHARPNESS, 1, 5), ranged(Enchantments.KNOCKBACK, 1, 2));

    // Wood Planks: same wood per type as Shield 1.
    private static final Map<ResourceKey<VillagerType>, Item> PLANKS_BY_TYPE = Map.of(
            VillagerType.PLAINS, Items.BIRCH_PLANKS,
            VillagerType.SAVANNA, Items.OAK_PLANKS,
            VillagerType.DESERT, Items.OAK_PLANKS,
            VillagerType.TAIGA, Items.SPRUCE_PLANKS,
            VillagerType.SNOW, ModItems.PINE_PLANKS.get(),
            VillagerType.SWAMP, Items.DARK_OAK_PLANKS,
            VillagerType.JUNGLE, Items.BAMBOO_PLANKS
    );
    private static final Item PLANKS_FALLBACK = Items.BIRCH_PLANKS;

    // Shield 1 (Apprentice).
    private static final Map<ResourceKey<VillagerType>, Item> SHIELD_1_BY_TYPE = Map.of(
            VillagerType.PLAINS, ModItems.BIRCH_SHIELD.get(),
            VillagerType.SAVANNA, ModItems.OAK_SHIELD.get(),
            VillagerType.DESERT, ModItems.OAK_SHIELD.get(),
            VillagerType.TAIGA, ModItems.SPRUCE_SHIELD.get(),
            VillagerType.SNOW, ModItems.PINE_SHIELD.get(),
            VillagerType.SWAMP, ModItems.DARK_OAK_SHIELD.get(),
            VillagerType.JUNGLE, ModItems.BAMBOO_SHIELD.get()
    );
    private static final Item SHIELD_1_FALLBACK = ModItems.BIRCH_SHIELD.get();

    // Shield 2 (Journeyman). Snow is Spruce in 1.21.11; Poplar in 26.3.
    private static final Map<ResourceKey<VillagerType>, Item> SHIELD_2_BY_TYPE = Map.of(
            VillagerType.PLAINS, ModItems.CHERRY_SHIELD.get(),
            VillagerType.SAVANNA, ModItems.ACACIA_SHIELD.get(),
            VillagerType.DESERT, ModItems.AZALEA_SHIELD.get(),
            VillagerType.TAIGA, ModItems.PALE_OAK_SHIELD.get(),
            VillagerType.SNOW, ModItems.SPRUCE_SHIELD.get(),
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

        // Expert (level 4): Enchanted Diamond Axe (villager type), Enchanted Diamond Spear (villager type) - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND_AXE, 1, 10, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.DIAMOND_AXE, AXE_ENCHANT_BY_TYPE, AXE_ENCHANT_FALLBACK, 16, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND_SPEAR, 1, 10, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.DIAMOND_SPEAR, SPEAR_ENCHANT_BY_TYPE, SPEAR_ENCHANT_FALLBACK, 16, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Diamond Sword (villager type). XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.DIAMOND_SWORD, 1, 14, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.DIAMOND_SWORD, SWORD_ENCHANT_BY_TYPE, SWORD_ENCHANT_FALLBACK, 20, MASTER_MAX_USES, 0));
    }
}
