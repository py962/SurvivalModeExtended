package com.terrainlab.trade;

import com.terrainlab.registry.ModDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared {@link VillagerTrades.ItemListing} implementations for TerrainLab's custom villager
 * trade tables. Prices respond to reputation but never to demand.
 */
public final class TradeListings {

    private TradeListings() {}

    // Vanilla rate, so reputation (hero, gossip) still moves prices; demand is frozen by
    // MerchantOfferNoDemandMixin instead.
    private static final float PRICE_MULTIPLIER = 0.05F;

    /**
     * Wraps a stack into the {@link ItemCost} MerchantOffer requires. Gear and enchanted books
     * are marked so that only an enchanted copy is accepted; the cost shows with a glint.
     */
    private static ItemCost toItemCost(ItemStack stack) {
        if (requiresEnchantment(stack)) {
            DataComponentExactPredicate marker = DataComponentExactPredicate.builder()
                    .expect(ModDataComponents.REQUIRES_ENCHANTMENT.get(), Unit.INSTANCE)
                    .expect(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
                    .build();
            return new ItemCost(stack.getItemHolder(), stack.getCount(), marker);
        }
        return new ItemCost(stack.getItemHolder(), stack.getCount(), DataComponentExactPredicate.EMPTY, stack);
    }

    /**
     * Gear: enchantable or damageable items, saddles, bundles, leads and enchanted books. Shears and
     * shields are traded plain both ways, so they are excluded.
     */
    private static boolean requiresEnchantment(ItemStack stack) {
        if (stack.getItem() instanceof ShearsItem || stack.getItem() instanceof ShieldItem) {
            return false;
        }
        return stack.has(DataComponents.ENCHANTABLE)
                || stack.isDamageableItem()
                || stack.is(Items.SADDLE)
                || stack.is(Items.BUNDLE)
                || stack.is(Items.LEAD)
                || stack.is(Items.ENCHANTED_BOOK);
    }

    /**
     * A fixed trade in either direction: "buying" means the villager buys {@code item} for
     * emeralds, "selling" means the villager sells it for emeralds.
     */
    public record SimpleTradeListing(
            boolean villagerBuys,
            Item item,
            int itemCount,
            int emeraldCount,
            int maxUses,
            int villagerXp
    ) implements VillagerTrades.ItemListing {

        public static SimpleTradeListing buying(Item item, int itemCount, int emeraldCount, int maxUses, int villagerXp) {
            return new SimpleTradeListing(true, item, itemCount, emeraldCount, maxUses, villagerXp);
        }

        public static SimpleTradeListing selling(Item item, int itemCount, int emeraldCount, int maxUses, int villagerXp) {
            return new SimpleTradeListing(false, item, itemCount, emeraldCount, maxUses, villagerXp);
        }

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            ItemStack itemStack = new ItemStack(item, itemCount);
            ItemStack emeraldStack = new ItemStack(Items.EMERALD, emeraldCount);

            ItemStack cost = villagerBuys ? itemStack : emeraldStack;
            ItemStack result = villagerBuys ? emeraldStack : itemStack;

            return new MerchantOffer(toItemCost(cost), Optional.empty(), result, maxUses, villagerXp, PRICE_MULTIPLIER);
        }
    }

    /**
     * A trade whose item depends on the trading villager's own {@link VillagerType} (not its
     * current position). {@code fallbackItem} applies to non-villager traders or unmapped types.
     */
    public record VillagerTypeVariantTradeListing(
            boolean villagerBuys,
            Map<ResourceKey<VillagerType>, Item> itemByType,
            Item fallbackItem,
            int itemCount,
            int emeraldCount,
            int maxUses,
            int villagerXp
    ) implements VillagerTrades.ItemListing {

        public static VillagerTypeVariantTradeListing buying(Map<ResourceKey<VillagerType>, Item> itemByType, Item fallbackItem,
                                                               int itemCount, int emeraldCount, int maxUses, int villagerXp) {
            return new VillagerTypeVariantTradeListing(true, itemByType, fallbackItem, itemCount, emeraldCount, maxUses, villagerXp);
        }

        public static VillagerTypeVariantTradeListing selling(Map<ResourceKey<VillagerType>, Item> itemByType, Item fallbackItem,
                                                                int itemCount, int emeraldCount, int maxUses, int villagerXp) {
            return new VillagerTypeVariantTradeListing(false, itemByType, fallbackItem, itemCount, emeraldCount, maxUses, villagerXp);
        }

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            Item resolvedItem = fallbackItem;

            if (trader instanceof Villager villager) {
                Holder<VillagerType> typeHolder = villager.getVillagerData().type();
                for (Map.Entry<ResourceKey<VillagerType>, Item> entry : itemByType.entrySet()) {
                    if (typeHolder.is(entry.getKey())) {
                        resolvedItem = entry.getValue();
                        break;
                    }
                }
            }

            ItemStack itemStack = new ItemStack(resolvedItem, itemCount);
            ItemStack emeraldStack = new ItemStack(Items.EMERALD, emeraldCount);

            ItemStack cost = villagerBuys ? itemStack : emeraldStack;
            ItemStack result = villagerBuys ? emeraldStack : itemStack;

            return new MerchantOffer(toItemCost(cost), Optional.empty(), result, maxUses, villagerXp, PRICE_MULTIPLIER);
        }
    }

    /**
     * A sell-only trade for a pre-enchanted item whose enchantments depend on the villager's
     * type. For a fixed enchantment set, pass an empty map and use {@code fallbackEnchantments}.
     *
     * <p>There is no buying counterpart: buy trades use {@link SimpleTradeListing} on the base
     * item, which matches any copy of it, enchanted or not.
     */
    public record VillagerTypeEnchantedSellListing(
            Item item,
            Map<ResourceKey<VillagerType>, List<EnchantmentInstance>> enchantmentsByType,
            List<EnchantmentInstance> fallbackEnchantments,
            int emeraldCount,
            int maxUses,
            int villagerXp
    ) implements VillagerTrades.ItemListing {

        /**
         * An enchantment whose level is rolled uniformly in {@code [minLevel, maxLevel]} each time
         * an offer is generated.
         */
        public record EnchantmentInstance(ResourceKey<Enchantment> enchantment, int minLevel, int maxLevel) {

            public static EnchantmentInstance fixed(ResourceKey<Enchantment> enchantment, int level) {
                return new EnchantmentInstance(enchantment, level, level);
            }

            public static EnchantmentInstance ranged(ResourceKey<Enchantment> enchantment, int minLevel, int maxLevel) {
                return new EnchantmentInstance(enchantment, minLevel, maxLevel);
            }

            int rollLevel(RandomSource random) {
                return minLevel == maxLevel ? minLevel : minLevel + random.nextInt(maxLevel - minLevel + 1);
            }
        }

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            List<EnchantmentInstance> enchantments = fallbackEnchantments;

            if (trader instanceof Villager villager) {
                Holder<VillagerType> typeHolder = villager.getVillagerData().type();
                for (Map.Entry<ResourceKey<VillagerType>, List<EnchantmentInstance>> entry : enchantmentsByType.entrySet()) {
                    if (typeHolder.is(entry.getKey())) {
                        enchantments = entry.getValue();
                        break;
                    }
                }
            }

            ItemStack itemStack = new ItemStack(item, 1);

            ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (EnchantmentInstance instance : enchantments) {
                Holder<Enchantment> enchantmentHolder = trader.level().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(instance.enchantment());
                mutableEnchantments.set(enchantmentHolder, instance.rollLevel(random));
            }
            itemStack.set(DataComponents.ENCHANTMENTS, mutableEnchantments.toImmutable());

            ItemStack emeraldStack = new ItemStack(Items.EMERALD, emeraldCount);

            return new MerchantOffer(toItemCost(emeraldStack), Optional.empty(), itemStack, maxUses, villagerXp, PRICE_MULTIPLIER);
        }
    }

    /**
     * A trade whose item is picked uniformly at random from {@code choices} each time an offer is generated.
     */
    public record RandomChoiceTradeListing(
            boolean villagerBuys,
            List<Item> choices,
            int itemCount,
            int emeraldCount,
            int maxUses,
            int villagerXp
    ) implements VillagerTrades.ItemListing {

        public static RandomChoiceTradeListing buying(List<Item> choices, int itemCount, int emeraldCount, int maxUses, int villagerXp) {
            return new RandomChoiceTradeListing(true, choices, itemCount, emeraldCount, maxUses, villagerXp);
        }

        public static RandomChoiceTradeListing selling(List<Item> choices, int itemCount, int emeraldCount, int maxUses, int villagerXp) {
            return new RandomChoiceTradeListing(false, choices, itemCount, emeraldCount, maxUses, villagerXp);
        }

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            Item resolvedItem = choices.get(random.nextInt(choices.size()));

            ItemStack itemStack = new ItemStack(resolvedItem, itemCount);
            ItemStack emeraldStack = new ItemStack(Items.EMERALD, emeraldCount);

            ItemStack cost = villagerBuys ? itemStack : emeraldStack;
            ItemStack result = villagerBuys ? emeraldStack : itemStack;

            return new MerchantOffer(toItemCost(cost), Optional.empty(), result, maxUses, villagerXp, PRICE_MULTIPLIER);
        }
    }

    /**
     * A sell-only trade for a base-variant potion whose effect depends on the villager's type.
     */
    public record VillagerTypePotionSellListing(
            Map<ResourceKey<VillagerType>, Holder<Potion>> potionByType,
            Holder<Potion> fallbackPotion,
            int emeraldCount,
            int maxUses,
            int villagerXp
    ) implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            Holder<Potion> potionHolder = fallbackPotion;

            if (trader instanceof Villager villager) {
                Holder<VillagerType> typeHolder = villager.getVillagerData().type();
                for (Map.Entry<ResourceKey<VillagerType>, Holder<Potion>> entry : potionByType.entrySet()) {
                    if (typeHolder.is(entry.getKey())) {
                        potionHolder = entry.getValue();
                        break;
                    }
                }
            }

            ItemStack itemStack = new ItemStack(Items.POTION, 1);
            itemStack.set(DataComponents.POTION_CONTENTS, new PotionContents(potionHolder));

            ItemStack emeraldStack = new ItemStack(Items.EMERALD, emeraldCount);

            return new MerchantOffer(toItemCost(emeraldStack), Optional.empty(), itemStack, maxUses, villagerXp, PRICE_MULTIPLIER);
        }
    }

    /**
     * A buy-only trade for one specific base-variant potion (chosen by villager type), so plain
     * water bottles and other potions are not accepted.
     */
    public record VillagerTypePotionBuyListing(
            Map<ResourceKey<VillagerType>, Holder<Potion>> potionByType,
            Holder<Potion> fallbackPotion,
            int emeraldCount,
            int maxUses,
            int villagerXp
    ) implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            Holder<Potion> potionHolder = fallbackPotion;

            if (trader instanceof Villager villager) {
                Holder<VillagerType> typeHolder = villager.getVillagerData().type();
                for (Map.Entry<ResourceKey<VillagerType>, Holder<Potion>> entry : potionByType.entrySet()) {
                    if (typeHolder.is(entry.getKey())) {
                        potionHolder = entry.getValue();
                        break;
                    }
                }
            }

            ItemCost cost = new ItemCost(Items.POTION.builtInRegistryHolder(), 1,
                    DataComponentExactPredicate.expect(DataComponents.POTION_CONTENTS, new PotionContents(potionHolder)));
            ItemStack emeraldStack = new ItemStack(Items.EMERALD, emeraldCount);

            return new MerchantOffer(cost, Optional.empty(), emeraldStack, maxUses, villagerXp, PRICE_MULTIPLIER);
        }
    }

    /**
     * A sell-only trade for a stack of tipped arrows whose potion contents depend on the villager's type.
     */
    public record VillagerTypeTippedArrowSellListing(
            Map<ResourceKey<VillagerType>, PotionContents> potionByType,
            PotionContents fallbackPotion,
            int itemCount,
            int emeraldCount,
            int maxUses,
            int villagerXp
    ) implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            PotionContents contents = fallbackPotion;

            if (trader instanceof Villager villager) {
                Holder<VillagerType> typeHolder = villager.getVillagerData().type();
                for (Map.Entry<ResourceKey<VillagerType>, PotionContents> entry : potionByType.entrySet()) {
                    if (typeHolder.is(entry.getKey())) {
                        contents = entry.getValue();
                        break;
                    }
                }
            }

            ItemStack itemStack = new ItemStack(Items.TIPPED_ARROW, itemCount);
            itemStack.set(DataComponents.POTION_CONTENTS, contents);

            ItemStack emeraldStack = new ItemStack(Items.EMERALD, emeraldCount);

            return new MerchantOffer(toItemCost(emeraldStack), Optional.empty(), itemStack, maxUses, villagerXp, PRICE_MULTIPLIER);
        }
    }

    /**
     * A sell-only trade for an enchanted book whose stored enchantments depend on the
     * villager's type.
     */
    public record VillagerTypeEnchantedBookSellListing(
            Map<ResourceKey<VillagerType>, List<VillagerTypeEnchantedSellListing.EnchantmentInstance>> enchantmentsByType,
            List<VillagerTypeEnchantedSellListing.EnchantmentInstance> fallbackEnchantments,
            int emeraldCount,
            int maxUses,
            int villagerXp
    ) implements VillagerTrades.ItemListing {

        @Override
        public MerchantOffer getOffer(ServerLevel level, Entity trader, RandomSource random) {
            List<VillagerTypeEnchantedSellListing.EnchantmentInstance> enchantments = fallbackEnchantments;

            if (trader instanceof Villager villager) {
                Holder<VillagerType> typeHolder = villager.getVillagerData().type();
                for (Map.Entry<ResourceKey<VillagerType>, List<VillagerTypeEnchantedSellListing.EnchantmentInstance>> entry : enchantmentsByType.entrySet()) {
                    if (typeHolder.is(entry.getKey())) {
                        enchantments = entry.getValue();
                        break;
                    }
                }
            }

            ItemStack itemStack = new ItemStack(Items.ENCHANTED_BOOK, 1);

            ItemEnchantments.Mutable mutableEnchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (VillagerTypeEnchantedSellListing.EnchantmentInstance instance : enchantments) {
                Holder<Enchantment> enchantmentHolder = trader.level().registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(instance.enchantment());
                mutableEnchantments.set(enchantmentHolder, instance.rollLevel(random));
            }
            itemStack.set(DataComponents.STORED_ENCHANTMENTS, mutableEnchantments.toImmutable());

            ItemStack emeraldStack = new ItemStack(Items.EMERALD, emeraldCount);

            return new MerchantOffer(toItemCost(emeraldStack), Optional.empty(), itemStack, maxUses, villagerXp, PRICE_MULTIPLIER);
        }
    }
}
