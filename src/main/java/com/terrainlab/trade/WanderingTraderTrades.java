package com.terrainlab.trade;

import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;
import java.util.Map;

import com.terrainlab.registry.ModItems;

/**
 * Wandering Trader trade pools. Every entry is equally weighted, single-use and gives no XP;
 * WanderingTraderSampleCountMixin draws 2 offers from {@link #BUY_POOL} and 20 from
 * {@link #SELL_POOL}. Potion entries reuse VillagerTypePotionSellListing with an empty
 * type map so the fallback potion is always used.
 */
public final class WanderingTraderTrades {

    private WanderingTraderTrades() {}

    private static final int MAX_USES = 1;
    private static final int NO_XP = 0;

    // The trader buys these from the player.
    public static final List<VillagerTrades.ItemListing> BUY_POOL = List.of(
            TradeListings.SimpleTradeListing.buying(Items.HAY_BLOCK, 1, 1, MAX_USES, NO_XP),
            new TradeListings.VillagerTypePotionBuyListing(Map.of(), Potions.WATER, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.buying(Items.BAKED_POTATO, 4, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.buying(Items.MILK_BUCKET, 1, 2, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.buying(Items.WATER_BUCKET, 1, 2, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.buying(Items.FERMENTED_SPIDER_EYE, 1, 3, MAX_USES, NO_XP)
    );

    // The trader sells these to the player.
    public static final List<VillagerTrades.ItemListing> SELL_POOL = List.of(
            TradeListings.SimpleTradeListing.selling(Items.PACKED_ICE, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.POWDER_SNOW_BUCKET, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PODZOL, 3, 1, MAX_USES, NO_XP),
            new TradeListings.VillagerTypePotionSellListing(Map.of(), Potions.INVISIBILITY, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BLUE_ICE, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.FERN, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.SUGAR_CANE, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.DANDELION, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.POPPY, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.ALLIUM, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.AZURE_BLUET, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.RED_TULIP, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.ORANGE_TULIP, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.WHITE_TULIP, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PINK_TULIP, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.OXEYE_DAISY, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.CORNFLOWER, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BLUE_ORCHID, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.LILY_OF_THE_VALLEY, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.OPEN_EYEBLOSSOM, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.WHEAT_SEEDS, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BEETROOT_SEEDS, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PUMPKIN_SEEDS, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.MELON_SEEDS, 1, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.NAME_TAG, 1, 1, MAX_USES, NO_XP),
            // TradeListings.SimpleTradeListing.selling(ModItems.SULFUR_SPIKE.get(), 2, 1, MAX_USES, NO_XP), // TODO: enable once ModItems.SULFUR_SPIKE exists
            TradeListings.SimpleTradeListing.selling(Items.WHITE_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.LIGHT_GRAY_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.GRAY_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BLACK_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BROWN_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.RED_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.ORANGE_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.YELLOW_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.LIME_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.GREEN_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.CYAN_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.LIGHT_BLUE_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BLUE_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PURPLE_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.MAGENTA_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PINK_DYE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.VINE, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PALE_HANGING_MOSS, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BROWN_MUSHROOM, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.RED_MUSHROOM, 3, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.LILY_PAD, 5, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.SMALL_DRIPLEAF, 2, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.SAND, 8, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.RED_SAND, 4, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.POINTED_DRIPSTONE, 2, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.ROOTED_DIRT, 2, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.MOSS_BLOCK, 2, 1, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PALE_MOSS_BLOCK, 2, 1, MAX_USES, NO_XP),
            // TradeListings.SimpleTradeListing.selling(ModItems.WILDFLOWERS.get(), 1, 1, MAX_USES, NO_XP), // TODO: vanilla Items.WILDFLOWERS exists
            // TradeListings.SimpleTradeListing.selling(Items.TALL_DRY_GRASS, 1, 1, MAX_USES, NO_XP), // TODO: vanilla constant is Items.DRY_TALL_GRASS
            // TradeListings.SimpleTradeListing.selling(ModItems.GOLDEN_DANDELION.get(), 1, 2, MAX_USES, NO_XP), // TODO: enable once ModItems.GOLDEN_DANDELION exists
            TradeListings.SimpleTradeListing.selling(Items.SEA_PICKLE, 1, 2, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.TROPICAL_FISH_BUCKET, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PUFFERFISH_BUCKET, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.CACTUS, 1, 2, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BRAIN_CORAL_BLOCK, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BUBBLE_CORAL_BLOCK, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.FIRE_CORAL_BLOCK, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.HORN_CORAL_BLOCK, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.TUBE_CORAL_BLOCK, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.FIREFLY_BUSH, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.SLIME_BALL, 1, 3, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.ACACIA_SAPLING, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.BIRCH_SAPLING, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.CHERRY_SAPLING, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.DARK_OAK_SAPLING, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.JUNGLE_SAPLING, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.MANGROVE_PROPAGULE, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.OAK_SAPLING, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.PALE_OAK_SAPLING, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.SPRUCE_SAPLING, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.NAUTILUS_SHELL, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(Items.RESIN_CLUMP, 1, 5, MAX_USES, NO_XP),
            TradeListings.SimpleTradeListing.selling(ModItems.NIGHTCAP_MUSHROOM.get(), 1, 5, MAX_USES, NO_XP)
    );
}
