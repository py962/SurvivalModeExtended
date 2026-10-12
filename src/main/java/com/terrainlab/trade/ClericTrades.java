package com.terrainlab.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;
import java.util.Map;

import com.terrainlab.registry.ModPotions;

/**
 * Replaces the vanilla Cleric trade table. The Master tier sells a base-duration potion
 * whose effect depends on the villager's biome type.
 */
public final class ClericTrades {

    private ClericTrades() {}

    private static final Holder<Potion> WARMTH = ModPotions.WARMTH;

    private static final Map<ResourceKey<VillagerType>, Holder<Potion>> POTION_BY_TYPE = Map.of(
            VillagerType.PLAINS, Potions.LEAPING,
            VillagerType.SAVANNA, Potions.SWIFTNESS,
            VillagerType.DESERT, Potions.FIRE_RESISTANCE,
            VillagerType.TAIGA, Potions.STRENGTH,
            VillagerType.SNOW, WARMTH,
            VillagerType.SWAMP, Potions.WATER_BREATHING,
            VillagerType.JUNGLE, Potions.NIGHT_VISION
    );
    private static final Holder<Potion> POTION_FALLBACK = Potions.LEAPING;

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CLERIC) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): Rotten Flesh, Sugar - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.ROTTEN_FLESH, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.ROTTEN_FLESH, 14, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.SUGAR, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.SUGAR, 14, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Spider Eye, Redstone - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.SPIDER_EYE, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.SPIDER_EYE, 7, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.REDSTONE, 3, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.REDSTONE, 1, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Gunpowder, Rabbit's Foot - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.GUNPOWDER, 4, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.GUNPOWDER, 2, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.RABBIT_FOOT, 4, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.RABBIT_FOOT, 2, 1, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Phantom Membrane, Ender Pearl - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.PHANTOM_MEMBRANE, 1, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.PHANTOM_MEMBRANE, 1, 3, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.ENDER_PEARL, 1, 4, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.selling(Items.ENDER_PEARL, 1, 6, EXPERT_MAX_USES, 10));

        // Master (level 5): buys and sells the villager type's potion. XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(new TradeListings.VillagerTypePotionBuyListing(POTION_BY_TYPE, POTION_FALLBACK, 5, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypePotionSellListing(POTION_BY_TYPE, POTION_FALLBACK, 7, MASTER_MAX_USES, 0));
    }
}
