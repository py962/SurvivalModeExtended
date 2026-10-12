package com.terrainlab.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;
import java.util.Map;

import com.terrainlab.registry.ModItems;

/**
 * Replaces the vanilla Shepherd trade table. Beds, banners, carpets and the Expert dye
 * source share a per-biome colour theme; furs vary by climate.
 */
public final class ShepherdTrades {

    private ShepherdTrades() {}

    private static final List<Item> NON_WHITE_WOOL_CHOICES = List.of(Items.BROWN_WOOL, Items.BLACK_WOOL, Items.GRAY_WOOL);

    // Per-biome colour theme shared by beds, banners and carpets.
    private static final Map<ResourceKey<VillagerType>, Item> BED_BY_TYPE = Map.of(
            VillagerType.PLAINS, Items.BLUE_BED,
            VillagerType.SAVANNA, Items.LIME_BED,
            VillagerType.DESERT, Items.PINK_BED,
            VillagerType.TAIGA, Items.RED_BED,
            VillagerType.SNOW, Items.PURPLE_BED,
            VillagerType.SWAMP, Items.MAGENTA_BED,
            VillagerType.JUNGLE, Items.BROWN_BED
    );

    private static final Map<ResourceKey<VillagerType>, Item> BANNER_BY_TYPE = Map.of(
            VillagerType.PLAINS, Items.BLUE_BANNER,
            VillagerType.SAVANNA, Items.LIME_BANNER,
            VillagerType.DESERT, Items.PINK_BANNER,
            VillagerType.TAIGA, Items.RED_BANNER,
            VillagerType.SNOW, Items.PURPLE_BANNER,
            VillagerType.SWAMP, Items.MAGENTA_BANNER,
            VillagerType.JUNGLE, Items.BROWN_BANNER
    );

    // TODO: confirm carpets should follow the biome colour theme rather than a fixed or random colour.
    private static final Map<ResourceKey<VillagerType>, Item> CARPET_BY_TYPE = Map.of(
            VillagerType.PLAINS, Items.BLUE_CARPET,
            VillagerType.SAVANNA, Items.LIME_CARPET,
            VillagerType.DESERT, Items.PINK_CARPET,
            VillagerType.TAIGA, Items.RED_CARPET,
            VillagerType.SNOW, Items.PURPLE_CARPET,
            VillagerType.SWAMP, Items.MAGENTA_CARPET,
            VillagerType.JUNGLE, Items.BROWN_CARPET
    );

    private static final Map<ResourceKey<VillagerType>, Item> LOCAL_DYE_SOURCE_2 = Map.of(
            VillagerType.PLAINS, Items.CORNFLOWER,
            VillagerType.SAVANNA, Items.SEA_PICKLE,
            VillagerType.DESERT, Items.CACTUS_FLOWER,
            VillagerType.TAIGA, Items.POPPY,
            VillagerType.SNOW, Items.SWEET_BERRIES,
            VillagerType.SWAMP, Items.LILAC,
            VillagerType.JUNGLE, Items.COCOA_BEANS
    );
    private static final Item LOCAL_DYE_SOURCE_2_FALLBACK = Items.CORNFLOWER;

    private static final Map<ResourceKey<VillagerType>, Item> GOAT_OR_CARIBOU_FUR = Map.of(
            VillagerType.PLAINS, ModItems.GOAT_FUR.get(),
            VillagerType.SAVANNA, ModItems.GOAT_FUR.get(),
            VillagerType.DESERT, ModItems.GOAT_FUR.get(),
            VillagerType.TAIGA, ModItems.CARIBOU_FUR.get(),
            VillagerType.SNOW, ModItems.CARIBOU_FUR.get(),
            VillagerType.SWAMP, ModItems.GOAT_FUR.get(),
            VillagerType.JUNGLE, ModItems.GOAT_FUR.get()
    );
    private static final Item GOAT_OR_CARIBOU_FUR_FALLBACK = ModItems.GOAT_FUR.get();

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.SHEPHERD) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): White Wool, Shears - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.WHITE_WOOL, 13, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.WHITE_WOOL, 9, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.SHEARS, 1, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.SHEARS, 1, 2, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Brown/Black/Grey Wool (random), Carpet (villager type) - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.RandomChoiceTradeListing.buying(NON_WHITE_WOOL_CHOICES, 13, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.RandomChoiceTradeListing.selling(NON_WHITE_WOOL_CHOICES, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.VillagerTypeVariantTradeListing.buying(CARPET_BY_TYPE, Items.WHITE_CARPET, 13, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.VillagerTypeVariantTradeListing.selling(CARPET_BY_TYPE, Items.WHITE_CARPET, 9, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Bed (villager type), Banner (villager type) - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.buying(BED_BY_TYPE, Items.WHITE_BED, 5, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.selling(BED_BY_TYPE, Items.WHITE_BED, 3, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.buying(BANNER_BY_TYPE, Items.WHITE_BANNER, 5, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.selling(BANNER_BY_TYPE, Items.WHITE_BANNER, 3, 1, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Local Dye Source 2 (villager type), Goat or Caribou Fur (villager type) - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.VillagerTypeVariantTradeListing.buying(LOCAL_DYE_SOURCE_2, LOCAL_DYE_SOURCE_2_FALLBACK, 9, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.VillagerTypeVariantTradeListing.selling(LOCAL_DYE_SOURCE_2, LOCAL_DYE_SOURCE_2_FALLBACK, 7, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.VillagerTypeVariantTradeListing.buying(GOAT_OR_CARIBOU_FUR, GOAT_OR_CARIBOU_FUR_FALLBACK, 3, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.VillagerTypeVariantTradeListing.selling(GOAT_OR_CARIBOU_FUR, GOAT_OR_CARIBOU_FUR_FALLBACK, 1, 1, EXPERT_MAX_USES, 10));

        // Master (level 5): Fur Cloak. XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(ModItems.FUR_CLOAK.get(), 1, 3, MASTER_MAX_USES, 0));
        master.add(TradeListings.SimpleTradeListing.selling(ModItems.FUR_CLOAK.get(), 1, 5, MASTER_MAX_USES, 0));
    }
}
