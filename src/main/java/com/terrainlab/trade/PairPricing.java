package com.terrainlab.trade;

import com.terrainlab.registry.ModItems;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reputation pricing for villagers, replacing vanilla's per-player price adjustment.
 * <p>
 * Every villager trade is one half of a pair: the villager buys an item for emeralds and sells the
 * same (or a matching) item for emeralds. Reputation moves both halves towards the pair's midpoint,
 * reaching it exactly at +100, so buying and reselling with one villager can never make a profit.
 * Rounding always favours the villager, nothing improves beyond +100, and negative reputation (down
 * to -100) widens the gap at the same rate. The change is applied to the side of each trade with
 * more items: item counts for cheap goods, emeralds for expensive ones. A pair whose two prices are
 * already equal (gold ingots) is a fixed exchange rate and never changes.
 * <p>
 * Hero of the Village counts as extra reputation, within the same +100 cap.
 */
public final class PairPricing {

    public static final int MAX_REPUTATION = 100;
    private static final int HERO_REPUTATION = 30;
    private static final int HERO_REPUTATION_PER_LEVEL = 6;
    /** For a trade with no partner, the share of its count it moves by at +/-100. */
    private static final double UNPAIRED_SWING = 0.15;

    private static Map<Item, Item> cookedForms;

    private PairPricing() {
    }

    /** Reputation for pricing: gossip (including mood penalties) plus Hero of the Village, capped to +/-100. */
    public static int effectiveReputation(Villager villager, Player player) {
        int reputation = villager.getPlayerReputation(player);
        MobEffectInstance hero = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (hero != null) {
            reputation += HERO_REPUTATION + HERO_REPUTATION_PER_LEVEL * hero.getAmplifier();
        }
        return Mth.clamp(reputation, -MAX_REPUTATION, MAX_REPUTATION);
    }

    /** Sets every offer's price for a player with this reputation, starting from the base prices. */
    public static void apply(MerchantOffers offers, int reputation) {
        restore(offers);
        if (reputation == 0) {
            return;
        }
        double t = Mth.clamp(reputation, -MAX_REPUTATION, MAX_REPUTATION) / (double) MAX_REPUTATION;

        Map<PairKey, MerchantOffer> sells = new HashMap<>();
        for (MerchantOffer offer : offers) {
            if (isSell(offer)) {
                sells.putIfAbsent(new PairKey(pairItem(offer.getResult().getItem()), offer.getMaxUses()), offer);
            }
        }
        for (MerchantOffer offer : offers) {
            if (!isBuy(offer)) {
                continue;
            }
            MerchantOffer partner = sells.remove(new PairKey(pairItem(offer.getBaseCostA().getItem()), offer.getMaxUses()));
            if (partner != null) {
                adjustPair(offer, partner, t);
            } else {
                adjustUnpaired(offer, t);
            }
        }
        for (MerchantOffer unmatched : sells.values()) {
            adjustUnpaired(unmatched, t);
        }
    }

    /** Back to base prices: clears cost adjustments and restores changed result counts. */
    public static void restore(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            offer.resetSpecialPriceDiff();
            restoreResult(offer);
        }
    }

    /** Restores changed result counts only (before saving, so they are never stored). */
    public static void restoreResults(MerchantOffers offers) {
        for (MerchantOffer offer : offers) {
            restoreResult(offer);
        }
    }

    private static void adjustPair(MerchantOffer buy, MerchantOffer sell, double t) {
        int itemsGiven = buy.getBaseCostA().getCount();
        int itemsReceived = baseResultCount(sell);
        if (itemsGiven > 1 || itemsReceived > 1) {
            // Cheap goods: change how many items change hands per emerald.
            if (itemsGiven == itemsReceived) {
                return;
            }
            double mid = (itemsGiven + itemsReceived) / 2.0;
            setCost(buy, (int) Math.ceil(itemsGiven - t * (itemsGiven - mid) - 1e-9));
            setResult(sell, (int) Math.floor(itemsReceived + t * (mid - itemsReceived) + 1e-9));
        } else {
            // Expensive goods: change the emeralds.
            int paid = baseResultCount(buy);
            int charged = sell.getBaseCostA().getCount();
            if (paid == charged) {
                return; // fixed exchange rate
            }
            double mid = (paid + charged) / 2.0;
            setResult(buy, (int) Math.floor(paid + t * (mid - paid) + 1e-9));
            setCost(sell, (int) Math.ceil(charged - t * (charged - mid) - 1e-9));
        }
    }

    private static void adjustUnpaired(MerchantOffer offer, double t) {
        int cost = offer.getBaseCostA().getCount();
        int result = baseResultCount(offer);
        if (cost >= result) {
            int swing = Math.max(1, (int) Math.round(cost * UNPAIRED_SWING));
            setCost(offer, (int) Math.ceil(cost - t * swing - 1e-9));
        } else {
            int swing = Math.max(1, (int) Math.round(result * UNPAIRED_SWING));
            setResult(offer, (int) Math.floor(result + t * swing + 1e-9));
        }
    }

    private static void setCost(MerchantOffer offer, int count) {
        ItemStack base = offer.getBaseCostA();
        int clamped = Mth.clamp(count, 1, base.getMaxStackSize());
        offer.setSpecialPriceDiff(clamped - base.getCount());
    }

    private static void setResult(MerchantOffer offer, int count) {
        ItemStack result = offer.getResult();
        MerchantOfferPricingAccess access = (MerchantOfferPricingAccess) offer;
        if (access.terrainlab$getBaseResultCount() < 0) {
            access.terrainlab$setBaseResultCount(result.getCount());
        }
        result.setCount(Mth.clamp(count, 1, result.getMaxStackSize()));
    }

    private static int baseResultCount(MerchantOffer offer) {
        int stored = ((MerchantOfferPricingAccess) offer).terrainlab$getBaseResultCount();
        return stored >= 0 ? stored : offer.getResult().getCount();
    }

    private static void restoreResult(MerchantOffer offer) {
        MerchantOfferPricingAccess access = (MerchantOfferPricingAccess) offer;
        int base = access.terrainlab$getBaseResultCount();
        if (base >= 0) {
            offer.getResult().setCount(base);
            access.terrainlab$setBaseResultCount(-1);
        }
    }

    private static boolean isBuy(MerchantOffer offer) {
        return !offer.getBaseCostA().is(Items.EMERALD) && offer.getResult().is(Items.EMERALD);
    }

    private static boolean isSell(MerchantOffer offer) {
        return offer.getBaseCostA().is(Items.EMERALD) && !offer.getResult().is(Items.EMERALD);
    }

    /** Butchers buy raw meat and sell it cooked; both count as the same item for pairing. */
    private static Item pairItem(Item item) {
        if (cookedForms == null) {
            cookedForms = Map.of(
                    Items.CHICKEN, Items.COOKED_CHICKEN,
                    Items.MUTTON, Items.COOKED_MUTTON,
                    Items.PORKCHOP, Items.COOKED_PORKCHOP,
                    Items.BEEF, Items.COOKED_BEEF,
                    ModItems.VENISON.get(), ModItems.COOKED_VENISON.get());
        }
        return cookedForms.getOrDefault(item, item);
    }

    /** A pair is the same item at the same level (levels have distinct use limits). */
    private record PairKey(Item item, int maxUses) {
        PairKey {
            Objects.requireNonNull(item);
        }
    }
}
