package com.terrainlab.trade;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.terrainlab.registry.ModItems;
import com.terrainlab.registry.ModPotions;

import static net.minecraft.world.item.alchemy.Potions.HARMING;

/**
 * Replaces the vanilla Fletcher trade table. Special feathers, tipped-arrow effects and
 * bow/crossbow enchantments all vary by villager biome type. Enchantment levels are fixed.
 */
public final class FletcherTrades {

    private FletcherTrades() {}

    // Blindness and Nausea have no potion (players get them from stews), so these arrows carry the
    // effect directly. The custom name keeps the "item.minecraft.tipped_arrow.effect.<name>" key.
    private static final PotionContents BLINDNESS = effectArrow("blindness", MobEffects.BLINDNESS);
    private static final PotionContents NAUSEA = effectArrow("nausea", MobEffects.NAUSEA);

    /** 20 s base, as the removed potions had; a tipped arrow applies an eighth of it. */
    private static PotionContents effectArrow(String name, Holder<MobEffect> effect) {
        return new PotionContents(Optional.empty(), Optional.empty(),
                List.of(new MobEffectInstance(effect, 400)), Optional.of(name));
    }

    private static final Holder<Potion> EXPLOSIVE = ModPotions.EXPLOSIVE;

    private static TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
        return TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance.fixed(enchantment, level);
    }


    // Enchanted Bow (Master). All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> BOW_ENCHANT_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.PUNCH, 2)),
            VillagerType.SAVANNA, List.of(fixed(Enchantments.UNBREAKING, 3)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.PUNCH, 1), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.DESERT, List.of(fixed(Enchantments.POWER, 2), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.SNOW, List.of(fixed(Enchantments.PUNCH, 1), fixed(Enchantments.POWER, 2)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.POWER, 5)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.INFINITY, 1))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> BOW_ENCHANT_FALLBACK =
            List.of(fixed(Enchantments.PUNCH, 2));

    // Enchanted Crossbow (Expert). All levels are fixed.
    private static final Map<ResourceKey<VillagerType>, List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance>> CROSSBOW_ENCHANT_BY_TYPE = Map.of(
            VillagerType.PLAINS, List.of(fixed(Enchantments.UNBREAKING, 3)),
            VillagerType.SAVANNA, List.of(fixed(Enchantments.MULTISHOT, 1)),
            VillagerType.TAIGA, List.of(fixed(Enchantments.QUICK_CHARGE, 2), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.DESERT, List.of(fixed(Enchantments.QUICK_CHARGE, 3)),
            VillagerType.SNOW, List.of(fixed(Enchantments.PIERCING, 2), fixed(Enchantments.QUICK_CHARGE, 2)),
            VillagerType.SWAMP, List.of(fixed(Enchantments.PIERCING, 2), fixed(Enchantments.UNBREAKING, 2)),
            VillagerType.JUNGLE, List.of(fixed(Enchantments.PIERCING, 4))
    );
    // Plains set.
    private static final List<TradeListings.VillagerTypeEnchantedSellListing.EnchantmentInstance> CROSSBOW_ENCHANT_FALLBACK =
            List.of(fixed(Enchantments.UNBREAKING, 3));

    // Special Feather: bird species per villager type.
    private static final Map<ResourceKey<VillagerType>, Item> FEATHER_BY_TYPE = Map.of(
            VillagerType.PLAINS, ModItems.GOOSE_FEATHER.get(),
            VillagerType.SAVANNA, ModItems.VULTURE_FEATHER.get(),
            VillagerType.DESERT, ModItems.VULTURE_FEATHER.get(),
            VillagerType.TAIGA, ModItems.OWL_FEATHER.get(),
            VillagerType.SNOW, ModItems.OWL_FEATHER.get(),
            VillagerType.SWAMP, ModItems.GOOSE_FEATHER.get(),
            VillagerType.JUNGLE, ModItems.PARROT_FEATHER.get()
    );
    private static final Item FEATHER_FALLBACK = ModItems.GOOSE_FEATHER.get();

    // Tipped Arrow: potion effect per villager type.
    private static final Map<ResourceKey<VillagerType>, PotionContents> TIPPED_ARROW_POTION_BY_TYPE = Map.of(
            VillagerType.PLAINS, BLINDNESS,
            VillagerType.SAVANNA, NAUSEA,
            VillagerType.DESERT, new PotionContents(Potions.WEAKNESS),
            VillagerType.TAIGA, new PotionContents(HARMING),
            VillagerType.SNOW, new PotionContents(Potions.SLOWNESS),
            VillagerType.SWAMP, new PotionContents(Potions.POISON),
            VillagerType.JUNGLE, new PotionContents(EXPLOSIVE)
    );
    private static final PotionContents TIPPED_ARROW_POTION_FALLBACK = BLINDNESS;

    private static final int NOVICE_MAX_USES = 16;
    private static final int APPRENTICE_MAX_USES = 12;
    private static final int JOURNEYMAN_MAX_USES = 8;
    private static final int EXPERT_MAX_USES = 4;
    private static final int MASTER_MAX_USES = 2;

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.FLETCHER) {
            return;
        }

        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = event.getTrades();

        // Novice (level 1): String, Stick - 1 XP/trade
        List<VillagerTrades.ItemListing> novice = trades.get(1);
        novice.clear();
        novice.add(TradeListings.SimpleTradeListing.buying(Items.STRING, 18, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.STRING, 14, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.buying(Items.STICK, 37, 1, NOVICE_MAX_USES, 1));
        novice.add(TradeListings.SimpleTradeListing.selling(Items.STICK, 27, 1, NOVICE_MAX_USES, 1));

        // Apprentice (level 2): Flint, Chicken Feather - 2 XP/trade
        List<VillagerTrades.ItemListing> apprentice = trades.get(2);
        apprentice.clear();
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.FLINT, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.FLINT, 7, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.buying(Items.FEATHER, 9, 1, APPRENTICE_MAX_USES, 2));
        apprentice.add(TradeListings.SimpleTradeListing.selling(Items.FEATHER, 7, 1, APPRENTICE_MAX_USES, 2));

        // Journeyman (level 3): Tripwire Hook, Special Feather (villager type) - 5 XP/trade
        List<VillagerTrades.ItemListing> journeyman = trades.get(3);
        journeyman.clear();
        journeyman.add(TradeListings.SimpleTradeListing.buying(Items.TRIPWIRE_HOOK, 5, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.SimpleTradeListing.selling(Items.TRIPWIRE_HOOK, 3, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.buying(FEATHER_BY_TYPE, FEATHER_FALLBACK, 4, 1, JOURNEYMAN_MAX_USES, 5));
        journeyman.add(TradeListings.VillagerTypeVariantTradeListing.selling(FEATHER_BY_TYPE, FEATHER_FALLBACK, 2, 1, JOURNEYMAN_MAX_USES, 5));

        // Expert (level 4): Tipped Arrow (villager type), Enchanted Crossbow (villager type) - 10 XP/trade
        List<VillagerTrades.ItemListing> expert = trades.get(4);
        expert.clear();
        expert.add(TradeListings.SimpleTradeListing.buying(Items.TIPPED_ARROW, 4, 1, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeTippedArrowSellListing(
                TIPPED_ARROW_POTION_BY_TYPE, TIPPED_ARROW_POTION_FALLBACK, 2, 1, EXPERT_MAX_USES, 10));
        expert.add(TradeListings.SimpleTradeListing.buying(Items.CROSSBOW, 1, 5, EXPERT_MAX_USES, 10));
        expert.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.CROSSBOW, CROSSBOW_ENCHANT_BY_TYPE, CROSSBOW_ENCHANT_FALLBACK, 11, EXPERT_MAX_USES, 10));

        // Master (level 5): Enchanted Bow (villager type). XP 0: no level above Master.
        List<VillagerTrades.ItemListing> master = trades.get(5);
        master.clear();
        master.add(TradeListings.SimpleTradeListing.buying(Items.BOW, 1, 9, MASTER_MAX_USES, 0));
        master.add(new TradeListings.VillagerTypeEnchantedSellListing(
                Items.BOW, BOW_ENCHANT_BY_TYPE, BOW_ENCHANT_FALLBACK, 15, MASTER_MAX_USES, 0));
    }
}
