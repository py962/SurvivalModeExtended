package com.terrainlab.entity;

import com.mojang.serialization.Codec;
import com.terrainlab.debug.GuideSpawnTrace;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.item.OminousBannerItem;
import com.terrainlab.item.StructureMapIcons;
import com.terrainlab.network.TrailTraderDialoguePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

/**
 * Shared base for the four Trail Trader guide variants (Ranger, Archeologist,
 * Mariner, Priest): the tiered map/relic trade chain, player bonding, and
 * one-guide-per-type uniqueness.
 * <p>
 * Uniqueness has two layers: a runtime registry stops two unbonded guides of
 * the same type existing in loaded chunks at once, and a persistent record
 * reserves the type once a guide is bonded, even while it is unloaded. The
 * first player to complete a trade becomes the guide's bonded player, so a
 * player can have at most one bonded guide of each type.
 */
public abstract class AbstractTrailTraderEntity extends WanderingTrader {

    private static final boolean DEBUG_LOGGING = false;

    private static final String TAG_TRADE_TIER = "TrailTraderTier";
    private static final String TAG_FIRST_TRADING_PLAYER = "TrailTraderFirstTradingPlayer";
    private static final String TAG_CHAIN_COMPLETE = "TrailTraderChainComplete";
    private static final String TAG_TRADES_INITIALIZED = "TrailTraderTradesInitialized";
    private static final String TAG_UNIQUE = "TrailTraderUnique";
    private static final String TAG_MAP_TARGETS = "TrailTraderMapTargets";
    private static final String TAG_MAP_ORIGINS = "TrailTraderMapOrigins";

    /**
     * Bonded-guide recall record, stored on the owner under
     * Player.PERSISTED_NBT_TAG so it survives death and is available while the
     * guide's chunk is unloaded. Layout:
     * {@code terrainlab_bonded_guides.<entity type id> = {Guide: uuid, Pos: long,
     * ForcedChunk: long?, ForcedAt: long?}}.
     */
    private static final String RECALL_ROOT = "terrainlab_bonded_guides";
    private static final String RECALL_GUIDE = "Guide";
    private static final String RECALL_POS = "Pos";
    private static final String RECALL_FORCED_CHUNK = "ForcedChunk";
    private static final String RECALL_FORCED_AT = "ForcedAt";
    /** How long a force-loaded recall chunk may fail to produce the guide before giving up. */
    private static final long RECALL_FORCE_TIMEOUT_TICKS = 20L * 60L;

    /**
     * A cached map destination is only reused while the guide is within this
     * distance (blocks) of where it last searched.
     */
    private static final double MAP_CACHE_REUSE_DISTANCE = 256.0D;
    private static final long NO_POS = Long.MIN_VALUE;

    private static final int MAX_TIER = 4;

    private static final int MAP_TRADE_MAX_USES = 12;
    private static final int RELIC_TRADE_MAX_USES = 1;
    private static final int MAP_SEARCH_RADIUS = 100;
    private static final byte MAP_SCALE = 2;

    /** Distance (blocks) beyond which a bonded guide teleports back towards its player. */
    private static final double BOND_FOLLOW_DISTANCE = 100.0D;
    private static final double BOND_FOLLOW_DISTANCE_SQR =
            BOND_FOLLOW_DISTANCE * BOND_FOLLOW_DISTANCE;

    /** Teleport ring around the player (blocks) for a bonded guide. */
    private static final double BOND_TELEPORT_MIN_DISTANCE = 16.0D;
    private static final double BOND_TELEPORT_MAX_DISTANCE = 80.0D;

    private static final int BOND_TELEPORT_ATTEMPTS = 16;

    private int tradeTier = 1;
    private boolean tradesInitialized = false;
    private boolean chainComplete = false;

    /** Whether this is the world's one-per-type guide: true for natural/worldgen spawns, false for eggs and /summon. */
    private boolean uniqueTrader = true;

    private boolean uniquenessChecked = false;

    /** Per-tier located destination and the guide position it was searched from (BlockPos#asLong); saved. */
    private final long[] mapTargets = {NO_POS, NO_POS, NO_POS, NO_POS};
    private final long[] mapOrigins = {NO_POS, NO_POS, NO_POS, NO_POS};

    /** Per-tier origin of a failed search; not saved, avoids repeating full searches this session. */
    private final long[] failedSearchOrigins = {NO_POS, NO_POS, NO_POS, NO_POS};

    /**
     * Copies of the two payment slots, captured server side by
     * MerchantContainerTrailTraderMixin. Vanilla shrinks the real stacks before
     * notifyTrade runs, so these are needed to return the exact relic paid.
     */
    private ItemStack lastPaymentA = ItemStack.EMPTY;
    private ItemStack lastPaymentB = ItemStack.EMPTY;

    /** Saved offers are checked once per load for outdated relic costs. */
    private boolean offersMigrated = false;

    /** The bonded player: whoever completed the first trade with this guide. */
    @Nullable
    private UUID firstTradingPlayerId;

    /**
     * Non-persistent registry of loaded Trail Traders per type; the value is a
     * heartbeat / spawn-claim timestamp (ms). An unbonded guide releases its slot
     * in remove() when its chunk unloads.
     * <p>
     * The spawn predicate may run on a worldgen thread, so this registry must
     * never touch EntitySectionStorage or scan entities.
     */
    private static final Map<ServerLevel, ConcurrentHashMap<EntityType<?>, Long>>
            ACTIVE_TRAIL_TRADERS = new ConcurrentHashMap<>();

    /**
     * Owner UUID for each slot in ACTIVE_TRAIL_TRADERS. No entry means a spawn
     * predicate has claimed the slot but the entity is not yet finalized.
     */
    private static final Map<ServerLevel, ConcurrentHashMap<EntityType<?>, UUID>>
            ACTIVE_TRAIL_TRADER_IDS = new ConcurrentHashMap<>();

    /** Expiry for a spawn claim that never completed; live traders refresh their slot every tick. */
    private static final long SPAWN_CLAIM_TIMEOUT_MS = 10_000L;

    protected AbstractTrailTraderEntity(
            EntityType<? extends WanderingTrader> type,
            Level level) {

        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Not a navigation follow: the guide wanders normally within range and
        // teleports to the player once they are farther than BOND_FOLLOW_DISTANCE.
        this.goalSelector.addGoal(
                5,
                new FollowTradingPlayerGoal(this)
        );

        // Keep clear of guide destinations; same priority as vanilla's avoid goals.
        this.goalSelector.addGoal(
                1,
                new GuideStructureAvoidance.AvoidGoal(this)
        );
    }

    /** No bond teleport attempts before this tick (set when every spot near the player is too close to a guide destination). */
    private int bondTeleportBlockedUntil;

    @Nullable
    private Player getFollowTargetPlayer() {
        if (this.firstTradingPlayerId == null) {
            return null;
        }

        // Bonded following is Overworld-only, so a Nether/End position is never a teleport target.
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return null;
        }

        if (serverLevel.dimension() != Level.OVERWORLD) {
            return null;
        }

        return serverLevel.getPlayerByUUID(this.firstTradingPlayerId);
    }

    private final class FollowTradingPlayerGoal extends Goal {

        private final AbstractTrailTraderEntity trader;

        FollowTradingPlayerGoal(AbstractTrailTraderEntity trader) {
            this.trader = trader;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        /** Following only runs while guide and player are both in the Overworld; otherwise the guide stays put. */
        private boolean bondedFollowAllowed(Player player) {
            return this.trader.level() instanceof ServerLevel serverLevel
                    && serverLevel.dimension() == Level.OVERWORLD
                    && player.level() == serverLevel
                    && !player.isSpectator();
        }

        @Override
        public boolean canUse() {
            Player player = getFollowTargetPlayer();

            return player != null
                    && bondedFollowAllowed(player)
                    && this.trader.distanceToSqr(player)
                    > BOND_FOLLOW_DISTANCE_SQR;
        }

        @Override
        public boolean canContinueToUse() {
            Player player = getFollowTargetPlayer();

            return player != null
                    && bondedFollowAllowed(player)
                    && this.trader.distanceToSqr(player)
                    > BOND_FOLLOW_DISTANCE_SQR;
        }

        @Override
        public void stop() {
            this.trader.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player player = getFollowTargetPlayer();

            if (player == null || !bondedFollowAllowed(player)) {
                this.trader.getNavigation().stop();
                return;
            }

            this.trader.teleportNearBondedPlayer(player);
        }
    }

    /**
     * Suppresses vanilla wandering while the bonded player is outside the
     * Overworld, so the guide stays at its stored position.
     */
    private boolean shouldSuppressNormalMovement() {
        if (this.firstTradingPlayerId == null) {
            return false;
        }

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        if (serverLevel.dimension() != Level.OVERWORLD) {
            return true;
        }

        Player player =
                serverLevel.getPlayerByUUID(
                        this.firstTradingPlayerId
                );

        return player != null
                && player.level() != serverLevel;
    }

    /** Runs the deferred uniqueness check and holds a dormant bonded guide in place around the vanilla tick. */
    @Override
    public void tick() {
        if (!this.uniquenessChecked
                && this.level() instanceof ServerLevel serverLevel) {

            this.uniquenessChecked = true;

            if (removeIfDuplicate(serverLevel)) {
                return;
            }
        }

        // Stop movement before the vanilla tick so a dormant bonded guide doesn't start wandering.
        if (shouldSuppressNormalMovement()) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        }

        super.tick();

        // ...and again after it, in case vanilla AI selected a movement goal.
        if (shouldSuppressNormalMovement()) {
            this.getNavigation().stop();
            this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        }

        if (!this.level().isClientSide()
                && this.isAlive()) {

            refreshTraderRegistryClaim();

            if (this.isAlive() && this.tickCount % 40 == 0) {
                updateOwnerRecallRecord();
            }
        }
    }

    /** Teleports this bonded guide to a safe spot 16-80 blocks from its player. */
    private void teleportNearBondedPlayer(Player player) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.tickCount < this.bondTeleportBlockedUntil) {
            return;   // player is at a guide destination; wait outside
        }

        if (serverLevel.dimension() != Level.OVERWORLD
                || player.level() != serverLevel
                || player.isSpectator()) {
            return;
        }

        RandomSource random = this.getRandom();

        for (int attempt = 0; attempt < BOND_TELEPORT_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;

            double distance =
                    BOND_TELEPORT_MIN_DISTANCE
                            + random.nextDouble()
                            * (BOND_TELEPORT_MAX_DISTANCE
                            - BOND_TELEPORT_MIN_DISTANCE);

            double x = player.getX() + Math.cos(angle) * distance;
            double z = player.getZ() + Math.sin(angle) * distance;

            int baseY = player.blockPosition().getY();

            // Search around the player's own Y so guides can follow underground.
            for (int verticalOffset = 0;
                 verticalOffset <= 16;
                 verticalOffset++) {

                if (tryTeleportToBondPosition(
                        serverLevel,
                        x,
                        baseY + verticalOffset,
                        z)) {

                    return;
                }

                if (verticalOffset != 0
                        && tryTeleportToBondPosition(
                        serverLevel,
                        x,
                        baseY - verticalOffset,
                        z)) {

                    return;
                }
            }
        }

        // Fallback: no safe spot found, so land on the player - unless the player is
        // at a guide destination, in which case retry in 5 seconds.
        if (GuideStructureAvoidance.isNearDestination(serverLevel, player.blockPosition())) {
            this.bondTeleportBlockedUntil = this.tickCount + 100;
            return;
        }
        this.teleportTo(
                player.getX(),
                player.getY(),
                player.getZ()
        );

        this.getNavigation().stop();
    }

    private boolean tryTeleportToBondPosition(
            ServerLevel serverLevel,
            double x,
            int y,
            double z) {

        BlockPos feetPos = BlockPos.containing(x, y, z);
        BlockPos headPos = feetPos.above();
        BlockPos belowPos = feetPos.below();

        BlockState belowState = serverLevel.getBlockState(belowPos);

        if (!belowState.isFaceSturdy(
                serverLevel,
                belowPos,
                Direction.UP)) {

            return false;
        }

        if (!serverLevel.getFluidState(feetPos).isEmpty()
                || !serverLevel.getFluidState(headPos).isEmpty()) {

            return false;
        }

        AABB destinationBox = this.getBoundingBox().move(
                x - this.getX(),
                y - this.getY(),
                z - this.getZ()
        );

        if (!serverLevel.noCollision(this, destinationBox)) {
            return false;
        }

        // Never land near a guide destination; checked last as it is the most expensive test.
        if (GuideStructureAvoidance.isNearDestination(serverLevel, feetPos)) {
            return false;
        }

        this.teleportTo(x, y, z);
        this.getNavigation().stop();

        return true;
    }

    protected abstract ItemLike[] getRelicItems();

    protected int[] getRelicRewardCounts() {
        return new int[]{1, 2, 4, 8};
    }

    protected abstract ItemLike getExperienceBottleItem();

    protected abstract String[] getMapDestinationNames();

    protected abstract TagKey<Structure>[] getMapDestinationStructureTags();

    protected abstract String getDialogueTranslationKeyPrefix();

    protected abstract ResourceKey<LootTable> getDonkeyCargoLootTable();

    /**
     * The enchantment (any level) a tier's Enchanted Book relic must carry, or
     * null if the relic isn't a book or any book will do.
     */
    @Nullable
    protected ResourceKey<Enchantment> getRequiredRelicEnchantment(int tier) {
        return null;
    }

    /** Dimension in which the given tier's destination is searched; Overworld unless overridden. */
    protected ResourceKey<Level> getMapDestinationDimension(int tier) {
        return Level.OVERWORLD;
    }

    private int getDialogueIndex() {
        return this.chainComplete ? 4 : (this.tradeTier - 1);
    }

    private String getDialogueTranslationKey() {
        return getDialogueTranslationKeyPrefix()
                + ".line"
                + (getDialogueIndex() + 1);
    }

    private void sendCurrentDialogueTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
                player,
                new TrailTraderDialoguePacket(
                        this.getUUID(),
                        getDialogueTranslationKey()
                )
        );
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        Player previousTradingPlayer = this.getTradingPlayer();

        super.setTradingPlayer(player);

        if (player instanceof ServerPlayer serverPlayer) {
            sendCurrentDialogueTo(serverPlayer);
            return;
        }

        // MerchantMenu calls setTradingPlayer(null) on close: clear the previous
        // player's client-side dialogue so it isn't drawn over the next MerchantScreen.
        if (previousTradingPlayer instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    TrailTraderDialoguePacket.clear(
                            this.getUUID()
                    )
            );
        }
    }

    @Override
    public MerchantOffers getOffers() {
        MerchantOffers offers = super.getOffers();

        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTraderTradeDupDebug] getOffers() ENTER side={} uuid={} tier={} tradesInitialized={} "
            // + "offers.size()={} offersIdentity={}",
            // this.level().isClientSide() ? "CLIENT" : "SERVER",
            // this.getUUID(),
            // this.tradeTier,
            // this.tradesInitialized,
            // offers.size(),
            // System.identityHashCode(offers)
            // );
        }

        if (!this.tradesInitialized) {
            this.tradesInitialized = true;

            for (int tier = 1; tier <= this.tradeTier; tier++) {
                addTierOffers(offers, tier);
            }

            if (DEBUG_LOGGING) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTraderTradeDupDebug] getOffers() POPULATED uuid={} newSize={} stack={}",
                // this.getUUID(),
                // offers.size(),
                // shortStackTrace()
                // );
            }
        }

        if (!this.offersMigrated) {
            this.offersMigrated = true;
            migrateRelicOffers(offers);
        }

        return offers;
    }

    private static String shortStackTrace() {
        StackTraceElement[] frames =
                Thread.currentThread().getStackTrace();

        StringBuilder sb = new StringBuilder();
        int printed = 0;

        for (StackTraceElement frame : frames) {
            if (frame.getClassName().equals(Thread.class.getName())
                    || frame.getClassName().equals(
                    AbstractTrailTraderEntity.class.getName())) {

                continue;
            }

            sb.append(frame).append(" <- ");
            printed++;

            if (printed >= 6) {
                break;
            }
        }

        return sb.toString();
    }

    @Override
    protected void updateTrades(ServerLevel level) {
    }

    private void addTierOffers(MerchantOffers offers, int tier) {
        int index = tier - 1;

        ItemLike[] relics = getRelicItems();
        int[] rewardCounts = getRelicRewardCounts();
        // Map offer: the placeholder is replaced with the real located map
        // when the trade completes.
        offers.add(new MerchantOffer(
                new ItemCost(Items.EMERALD, 1),
                buildStubMap(tier),
                MAP_TRADE_MAX_USES,
                1,
                0.05F
        ));

        offers.add(new MerchantOffer(
                relicCost(relics[index]),
                new ItemStack(getExperienceBottleItem(), rewardCounts[index]),
                RELIC_TRADE_MAX_USES,
                1,
                0.0F
        ));
    }

    /**
     * The cost for a relic offer. Usually just the item type. The ominous banner also expects
     * its pattern layers: only the predicate is sent to the client (the cost's display stack is
     * rebuilt from it), so without them the trade shows a plain white banner.
     */
    private ItemCost relicCost(ItemLike relic) {
        if (relic.asItem() instanceof OminousBannerItem) {
            ItemStack shown = OminousBannerItem.createStack(this.level().registryAccess());
            return new ItemCost(relic.asItem().builtInRegistryHolder(), 1,
                    DataComponentExactPredicate.someOf(shown.getComponents(), DataComponents.BANNER_PATTERNS));
        }
        return new ItemCost(relic, 1);
    }

    /**
     * Rebuilds any relic offer whose cost item no longer matches the current
     * relic table (e.g. an old tier-3 offer asking for a blank map), keeping its
     * use count. Offers are laid out in tier order as [map, relic] pairs.
     */
    private void migrateRelicOffers(MerchantOffers offers) {
        ItemLike[] relics = getRelicItems();

        for (int tier = 1; tier <= MAX_TIER && tier <= relics.length; tier++) {
            migrateMapOffer(offers, tier);

            int i = (tier - 1) * 2 + 1;

            if (i >= offers.size()) {
                break;
            }

            MerchantOffer old = offers.get(i);
            Item costItem = old.getItemCostA().item().value();
            Item expected = relics[tier - 1].asItem();

            if (costItem == Items.EMERALD) {
                continue;
            }
            // Banner offers saved before the cost carried its patterns are rebuilt too.
            if (costItem == expected
                    && !(expected instanceof OminousBannerItem && old.getItemCostA().components().isEmpty())) {
                continue;
            }

            offers.set(i, new MerchantOffer(
                    relicCost(expected),
                    Optional.empty(),
                    old.getResult().copy(),
                    old.getUses(),
                    old.getMaxUses(),
                    old.getXp(),
                    old.getPriceMultiplier(),
                    old.getDemand()
            ));
        }
    }

    /**
     * Rebuilds a tier's map offer whose placeholder is outdated (a blank map with a literal
     * "Map to X" custom name, from before charts used translatable names), keeping its use count.
     */
    private void migrateMapOffer(MerchantOffers offers, int tier) {
        int i = (tier - 1) * 2;

        if (i >= offers.size()) {
            return;
        }

        MerchantOffer old = offers.get(i);

        if (mapTierOf(old) != tier) {
            return;
        }

        ItemStack result = old.getResult();

        if (!result.has(DataComponents.CUSTOM_NAME)) {
            return;
        }

        offers.set(i, new MerchantOffer(
                old.getItemCostA(),
                old.getItemCostB(),
                buildStubMap(tier),
                old.getUses(),
                old.getMaxUses(),
                old.getXp(),
                old.getPriceMultiplier(),
                old.getDemand()
        ));
    }

    /** 1-based tier whose relic offer this is, or 0 if it isn't a relic offer. */
    private int relicTierOf(MerchantOffer offer) {
        Item cost = offer.getItemCostA().item().value();

        if (cost == Items.EMERALD) {
            return 0;
        }

        ItemLike[] relics = getRelicItems();

        for (int tier = 1; tier <= relics.length; tier++) {
            if (relics[tier - 1].asItem() == cost) {
                return tier;
            }
        }

        return 0;
    }

    /**
     * 1-based tier whose map offer this is, matched by the placeholder's name,
     * or 0. Earlier tiers' maps stay on sale, so the tier comes from the offer,
     * not the guide's current tier.
     */
    private int mapTierOf(MerchantOffer offer) {
        if (offer.getItemCostA().item().value() != Items.EMERALD) {
            return 0;
        }

        String[] destinations = getMapDestinationNames();

        for (int tier = 1; tier <= destinations.length; tier++) {
            if (hasChartName(offer.getResult(), tier)) {
                return tier;
            }
        }

        return 0;
    }

    /**
     * Translation key of a tier's chart name, e.g. {@code item.terrainlab.guide_chart.ancient_city}
     * for "Ancient City". Used as the ITEM_NAME of both the placeholder and the located chart.
     */
    private String chartNameKey(int tier) {
        return "item.terrainlab.guide_chart."
                + getMapDestinationNames()[tier - 1].toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * Whether the stack carries a tier's chart name: the translatable item name, or the literal
     * "Map to X" custom name placeholders were given before charts used translatable names.
     */
    private boolean hasChartName(ItemStack stack, int tier) {
        Component itemName = stack.get(DataComponents.ITEM_NAME);

        if (itemName != null
                && itemName.getContents() instanceof TranslatableContents translatable
                && translatable.getKey().equals(chartNameKey(tier))) {
            return true;
        }

        Component customName = stack.get(DataComponents.CUSTOM_NAME);

        return customName != null
                && customName.getString().equals("Map to " + getMapDestinationNames()[tier - 1]);
    }

    /**
     * The icon for a tier's chart, from the shared structure table, or null for destinations
     * with no icon of their own (those get a target X).
     */
    @Nullable
    private StructureMapIcons.Icon chartIcon(int tier) {
        TagKey<Structure> tag = getMapDestinationStructureTags()[tier - 1];

        return tag == null
                ? null
                : StructureMapIcons.forTag(this.level().registryAccess(), tag);
    }

    /**
     * Called server side by MerchantContainerTrailTraderMixin whenever the
     * payment slots are re-evaluated. Records copies of both payment stacks and
     * returns false if a relic offer is paid with the wrong relic, in which case
     * the mixin clears the trade result.
     */
    public boolean acceptsPayment(
            @Nullable MerchantOffer offer,
            ItemStack paymentA,
            ItemStack paymentB) {

        this.lastPaymentA = paymentA.copy();
        this.lastPaymentB = paymentB.copy();

        if (offer == null) {
            return true;
        }

        int tier = relicTierOf(offer);

        if (tier == 0) {
            return true;
        }

        ItemStack paid = offer.getItemCostA().test(paymentA)
                ? paymentA
                : paymentB;

        if (isValidRelic(tier, paid)) {
            return true;
        }

        Player player = this.getTradingPlayer();

        if (player != null) {
            player.displayClientMessage(
                    Component.translatableWithFallback(
                            "message.terrainlab.trail_trader.wrong_relic",
                            "That's not the one I'm looking for."
                    ),
                    true
            );
        }

        return false;
    }

    /**
     * Custom-data key set by the loot tables that hold relic maps. Its value identifies which map
     * it is (e.g. "sunken_chart"), so any guide can ask for any of them.
     */
    public static final String RELIC_MAP_TAG = "terrainlab_relic_map";

    /** The relic map id (value of {@link #RELIC_MAP_TAG}) this guide accepts as his map relic. */
    protected abstract String getRequiredRelicMap();

    /**
     * Enchanted Book relics must carry the tier's required enchantment. Map
     * relics must be the specific relic map this guide asks for, identified by the
     * {@link #RELIC_MAP_TAG} its loot table gives it.
     */
    protected boolean isValidRelic(int tier, ItemStack stack) {
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ResourceKey<Enchantment> required = getRequiredRelicEnchantment(tier);

            if (required == null) {
                return true;
            }

            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);

            if (stored == null) {
                return false;
            }

            for (Holder<Enchantment> enchantment : stored.keySet()) {
                if (enchantment.is(required)) {
                    return true;
                }
            }

            return false;
        }

        if (stack.is(Items.FILLED_MAP)) {
            // Only the specific relic map counts; buried-treasure, cartographer and camp maps
            // and guide charts never do.
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            return data != null && getRequiredRelicMap().equals(data.copyTag().getStringOr(RELIC_MAP_TAG, ""));
        }

        return true;
    }

    /** One of the exact relic the player paid with; falls back to a fresh stack if no payment copy matches. */
    private ItemStack exactPaidRelic(MerchantOffer offer, int tier) {
        for (ItemStack paid : new ItemStack[]{this.lastPaymentA, this.lastPaymentB}) {
            if (!paid.isEmpty() && offer.getItemCostA().test(paid)) {
                return paid.copyWithCount(1);
            }
        }

        return buildRelicRefundStack(getRelicItems()[tier - 1]);
    }

    /** Fresh refund stack for a relic, including components some relics (OminousBannerItem) need to render. */
    private ItemStack buildRelicRefundStack(ItemLike relic) {
        if (relic.asItem() instanceof OminousBannerItem) {
            return OminousBannerItem.createStack(
                    this.level().registryAccess()
            );
        }

        return new ItemStack(relic, 1);
    }

    /**
     * Builds the located map for a tier, or null if the destination can't be
     * found.
     * <p>
     * The search radius of findNearestMapStructure is in rings of the
     * structure's placement grid (tens of thousands of blocks), so a null result
     * means the structure cannot exist in this world. Those cases are detected
     * up front where possible and logged. Results are cached per tier (and
     * saved) while the guide stays within MAP_CACHE_REUSE_DISTANCE.
     */
    @Nullable
    private ItemStack buildDestinationMap(int tier) {
        int index = tier - 1;

        String destinationName = getMapDestinationNames()[index];
        TagKey<Structure> structureTag = getMapDestinationStructureTags()[index];
        ResourceKey<Level> destinationDimension = getMapDestinationDimension(tier);

        if (structureTag == null
                || !(this.level() instanceof ServerLevel sourceLevel)) {

            return null;
        }

        // The destination may be in another dimension (e.g. the Priest's Nether tiers).
        ServerLevel destinationLevel =
                sourceLevel.getServer().getLevel(destinationDimension);

        if (destinationLevel == null) {
            com.terrainlab.TerrainLab.LOGGER.error(
                    "[TrailTrader] {} map to {}: dimension {} is not loaded",
                    this.getType(), destinationName, destinationDimension.identifier());
            return null;
        }

        BlockPos origin = this.blockPosition();
        BlockPos found;

        if (this.mapTargets[index] != NO_POS
                && this.mapOrigins[index] != NO_POS
                && BlockPos.of(this.mapOrigins[index]).closerThan(origin, MAP_CACHE_REUSE_DISTANCE)) {

            found = BlockPos.of(this.mapTargets[index]);

        } else if (this.failedSearchOrigins[index] != NO_POS
                && BlockPos.of(this.failedSearchOrigins[index]).closerThan(origin, MAP_CACHE_REUSE_DISTANCE)) {

            return null;

        } else {
            String problem = destinationProblem(destinationLevel, structureTag);

            if (problem != null) {
                com.terrainlab.TerrainLab.LOGGER.error(
                        "[TrailTrader] {} cannot sell a map to {}: {}",
                        this.getType(), destinationName, problem);
                this.failedSearchOrigins[index] = origin.asLong();
                return null;
            }

            // Overworld guide searching the Nether: start from the scaled (/8) position.
            BlockPos searchPosition;

            if (destinationDimension == Level.NETHER
                    && sourceLevel.dimension() == Level.OVERWORLD) {

                searchPosition = new BlockPos(
                        Math.floorDiv(origin.getX(), 8),
                        origin.getY(),
                        Math.floorDiv(origin.getZ(), 8)
                );

            } else {
                searchPosition = origin;
            }

            found = destinationLevel.findNearestMapStructure(
                    structureTag,
                    searchPosition,
                    MAP_SEARCH_RADIUS,
                    false
            );

            if (found == null) {
                com.terrainlab.TerrainLab.LOGGER.error(
                        "[TrailTrader] {} cannot sell a map to {}: no {} generates anywhere "
                                + "within the full search range of {} - the structure can "
                                + "never place (check its template pool / .nbt piece and biomes)",
                        this.getType(), destinationName, structureTag.location(), searchPosition);
                this.failedSearchOrigins[index] = origin.asLong();
                return null;
            }

            this.mapTargets[index] = found.asLong();
            this.mapOrigins[index] = origin.asLong();
        }

        // Marked with the destination's icon where the structure table has one, otherwise a
        // target X.
        StructureMapIcons.Icon icon = chartIcon(tier);
        Holder<MapDecorationType> decoration = icon != null
                ? icon.decoration().get()
                : MapDecorationTypes.TARGET_X;

        // Create the map against the destination dimension, not the guide's.
        ItemStack map = MapItem.create(
                destinationLevel,
                found.getX(),
                found.getZ(),
                MAP_SCALE,
                true,
                true
        );

        MapItem.renderBiomePreviewMap(destinationLevel, map);

        MapItemSavedData.addTargetDecoration(
                map,
                found,
                "+",
                decoration
        );

        map.set(
                DataComponents.ITEM_NAME,
                Component.translatable(chartNameKey(tier))
        );

        return map;
    }

    @Nullable
    private static String destinationProblem(
            ServerLevel level,
            TagKey<Structure> tag) {

        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) {
            return "structures are turned off in this world";
        }

        Optional<HolderSet.Named<Structure>> structures =
                level.registryAccess()
                        .lookupOrThrow(Registries.STRUCTURE)
                        .get(tag);

        if (structures.isEmpty() || structures.get().size() == 0) {
            return "locate tag " + tag.location()
                    + " is missing or matches no structure (wrong structure id in the tag file?)";
        }

        for (Holder<Structure> structure : structures.get()) {
            if (!level.getChunkSource()
                    .getGeneratorState()
                    .getPlacementsForStructure(structure)
                    .isEmpty()) {

                return null;
            }
        }

        return "no structure in " + tag.location() + " has a structure set placing it in "
                + level.dimension().identifier();
    }

    /**
     * The placeholder sold for a tier's chart: a blank map named like the chart. It is swapped
     * for the located chart when the trade completes.
     */
    private ItemStack buildStubMap(int tier) {
        ItemStack stack = new ItemStack(Items.MAP);

        stack.set(
                DataComponents.ITEM_NAME,
                Component.translatable(chartNameKey(tier))
        );

        return stack;
    }

    /**
     * Whether the stack is a purchased placeholder for a tier's chart: a blank map carrying the
     * chart's name (translatable, or the literal name older placeholders had).
     */
    private boolean isStub(ItemStack stack, int tier) {
        if (!stack.is(Items.MAP) || stack.has(DataComponents.MAP_ID)) {
            return false;
        }

        return hasChartName(stack, tier);
    }

    /**
     * Replaces one purchased placeholder map with the replacement. Checks the
     * cursor first (a normal click puts the purchase there before notifyTrade),
     * then the inventory (shift-click).
     */
    private static void swapPurchasedStub(
            Player player,
            Predicate<ItemStack> isStub,
            ItemStack replacement) {

        ItemStack carried = player.containerMenu.getCarried();

        if (isStub.test(carried)) {
            if (carried.getCount() == 1) {
                player.containerMenu.setCarried(replacement);
            } else {
                carried.shrink(1);
                giveOrDrop(player, replacement);
            }

            return;
        }

        for (int slot = 0;
             slot < player.getInventory().getContainerSize();
             slot++) {

            ItemStack stack = player.getInventory().getItem(slot);

            if (!isStub.test(stack)) {
                continue;
            }

            if (stack.getCount() == 1) {
                player.getInventory().setItem(slot, replacement);
            } else {
                stack.shrink(1);
                giveOrDrop(player, replacement);
            }

            return;
        }

        giveOrDrop(player, replacement);
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Swaps the purchased placeholder for the real located map, or takes the
     * placeholder back and refunds the payment if the destination can't be
     * found.
     */
    private void deliverPurchasedMap(
            Player player,
            MerchantOffer offer,
            int tier) {

        String destinationName = getMapDestinationNames()[tier - 1];
        Predicate<ItemStack> isStub = stack -> isStub(stack, tier);

        ItemStack located = buildDestinationMap(tier);

        if (located != null) {
            swapPurchasedStub(player, isStub, located);
            return;
        }

        swapPurchasedStub(player, isStub, offer.getCostA().copy());

        player.displayClientMessage(
                Component.translatableWithFallback(
                        "message.terrainlab.trail_trader.no_destination",
                        "I can't find a %s anywhere in this world.",
                        destinationName
                ),
                false
        );
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        super.notifyTrade(offer);

        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Player player = this.getTradingPlayer();

        int mapTier = mapTierOf(offer);

        if (mapTier > 0 && player != null) {
            deliverPurchasedMap(player, offer, mapTier);
        }

        // The first completed trade bonds this guide to the player and reserves its
        // type persistently so no replacement generates while it is unloaded.
        if (this.firstTradingPlayerId == null && player != null) {
            this.firstTradingPlayerId = player.getUUID();

            if (this.uniqueTrader) {
                livingTraders(serverLevel).put(
                        this.getType(),
                        this.getUUID()
                );
            }

            if (DEBUG_LOGGING) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTrader] {} {} bonded permanently to player {}",
                // this.getType(),
                // this.getUUID(),
                // this.firstTradingPlayerId
                // );
            }
        }

        updateOwnerRecallRecord();

        int relicTier = relicTierOf(offer);

        if (relicTier == 0 || relicTier != this.tradeTier) {
            return;
        }

        // Relics are never kept: hand back exactly what was paid.
        if (player != null) {
            giveOrDrop(player, exactPaidRelic(offer, relicTier));
        }

        if (player instanceof ServerPlayer relicPlayer) {
            com.terrainlab.advancement.ModAdvancements.award(relicPlayer, "guide_relic");
            if (this.tradeTier >= MAX_TIER) {
                com.terrainlab.advancement.ModAdvancements.award(relicPlayer,
                        "quest_" + BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()).getPath());
            }
        }

        if (this.tradeTier < MAX_TIER) {
            this.tradeTier++;

            MerchantOffers offers = this.getOffers();

            addTierOffers(
                    offers,
                    this.tradeTier
            );

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendMerchantOffers(
                        serverPlayer.containerMenu.containerId,
                        offers,
                        this.tradeTier,
                        this.getVillagerXp(),
                        this.showProgressBar(),
                        this.canRestock()
                );
            }
        } else {
            this.chainComplete = true;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            sendCurrentDialogueTo(serverPlayer);
        }
    }

    private static String recallKey(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private static CompoundTag recallEntries(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
        data.put(Player.PERSISTED_NBT_TAG, persisted);

        CompoundTag all = persisted.getCompoundOrEmpty(RECALL_ROOT);
        persisted.put(RECALL_ROOT, all);

        return all;
    }

    /**
     * Records this bonded guide's identity and position on its owner so the
     * recall check can find it after its chunk unloads. Only the unique guide is
     * recorded, so a spawn-egg guide can't overwrite the real one's record.
     */
    private void updateOwnerRecallRecord() {
        if (!this.uniqueTrader
                || this.firstTradingPlayerId == null
                || !(this.level() instanceof ServerLevel serverLevel)
                || serverLevel.dimension() != Level.OVERWORLD) {

            return;
        }

        ServerPlayer owner = serverLevel.getServer()
                .getPlayerList()
                .getPlayer(this.firstTradingPlayerId);

        if (owner == null) {
            return;
        }

        CompoundTag all = recallEntries(owner);
        String key = recallKey(this.getType());
        CompoundTag entry = all.getCompoundOrEmpty(key);

        entry.putIntArray(RECALL_GUIDE, UUIDUtil.uuidToIntArray(this.getUUID()));
        entry.putLong(RECALL_POS, this.blockPosition().asLong());
        all.put(key, entry);
    }

    /** Drops this guide's record from its online owner. */
    private void clearOwnerRecallRecord(ServerLevel serverLevel) {
        if (this.firstTradingPlayerId == null) {
            return;
        }

        ServerPlayer owner = serverLevel.getServer()
                .getPlayerList()
                .getPlayer(this.firstTradingPlayerId);

        if (owner == null) {
            return;
        }

        CompoundTag all = recallEntries(owner);
        String key = recallKey(this.getType());
        CompoundTag entry = all.getCompoundOrEmpty(key);

        Optional<int[]> ids = entry.getIntArray(RECALL_GUIDE);

        if (ids.isPresent()
                && ids.get().length == 4
                && UUIDUtil.uuidFromIntArray(ids.get()).equals(this.getUUID())) {

            releaseForcedChunk(serverLevel.getServer().overworld(), entry);
            all.remove(key);
        }
    }

    private static void releaseForcedChunk(ServerLevel level, CompoundTag entry) {
        Optional<Long> chunk = entry.getLong(RECALL_FORCED_CHUNK);

        if (chunk.isPresent()) {
            ChunkPos pos = new ChunkPos(chunk.get());
            level.setChunkForced(pos.x, pos.z, false);
            entry.remove(RECALL_FORCED_CHUNK);
        }

        entry.remove(RECALL_FORCED_AT);
    }

    /**
     * Every ~5 s (driven from tickCargoDonkeyLeashWatchdog), brings each
     * Overworld player's bonded guides back within range, since the follow goal
     * only runs while the guide's chunk ticks. A loaded guide more than
     * BOND_FOLLOW_DISTANCE away is teleported; an unloaded one has its last known
     * chunk force-loaded until it appears, and its record is dropped if it hasn't
     * after RECALL_FORCE_TIMEOUT_TICKS. Players outside the Overworld are skipped.
     */
    private static void tickBondedGuideRecall(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }

        long now = level.getGameTime();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }

            CompoundTag persisted = player.getPersistentData()
                    .getCompoundOrEmpty(Player.PERSISTED_NBT_TAG);
            CompoundTag all = persisted.getCompoundOrEmpty(RECALL_ROOT);

            if (all.isEmpty()) {
                continue;
            }

            for (String key : List.copyOf(all.keySet())) {
                CompoundTag entry = all.getCompoundOrEmpty(key);
                Optional<int[]> ids = entry.getIntArray(RECALL_GUIDE);

                if (ids.isEmpty() || ids.get().length != 4) {
                    all.remove(key);
                    continue;
                }

                Entity loaded = level.getEntity(UUIDUtil.uuidFromIntArray(ids.get()));

                if (loaded instanceof AbstractTrailTraderEntity guide
                        && guide.isAlive()) {

                    releaseForcedChunk(level, entry);

                    if (guide.distanceToSqr(player) > BOND_FOLLOW_DISTANCE_SQR) {
                        guide.teleportNearBondedPlayer(player);
                    }

                    entry.putLong(RECALL_POS, guide.blockPosition().asLong());
                    continue;
                }

                Optional<Long> lastPos = entry.getLong(RECALL_POS);

                if (lastPos.isEmpty()) {
                    continue;
                }

                Optional<Long> forcedAt = entry.getLong(RECALL_FORCED_AT);

                if (forcedAt.isPresent()) {
                    if (now - forcedAt.get() > RECALL_FORCE_TIMEOUT_TICKS) {
                        releaseForcedChunk(level, entry);
                        all.remove(key);

                        if (DEBUG_LOGGING) {
                            // com.terrainlab.TerrainLab.LOGGER.info(
                            // "[TrailTrader] recall: {} for {} not found at its last "
                            // + "position - record dropped",
                            // key, player.getScoreboardName());
                        }
                    }

                    continue;
                }

                ChunkPos chunk = new ChunkPos(BlockPos.of(lastPos.get()));

                level.setChunkForced(chunk.x, chunk.z, true);
                entry.putLong(RECALL_FORCED_CHUNK, chunk.toLong());
                entry.putLong(RECALL_FORCED_AT, now);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);

        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTraderTradeDupDebug] addAdditionalSaveData() uuid={} writing "
            // + "tradesInitialized={} tradeTier={}",
            // this.getUUID(),
            // this.tradesInitialized,
            // this.tradeTier
            // );
        }

        output.putInt(
                TAG_TRADE_TIER,
                this.tradeTier
        );

        output.putBoolean(
                TAG_CHAIN_COMPLETE,
                this.chainComplete
        );

        output.putBoolean(
                TAG_TRADES_INITIALIZED,
                this.tradesInitialized
        );

        output.putBoolean(
                TAG_UNIQUE,
                this.uniqueTrader
        );

        output.storeNullable(
                TAG_FIRST_TRADING_PLAYER,
                UUIDUtil.CODEC,
                this.firstTradingPlayerId
        );

        output.store(TAG_MAP_TARGETS, Codec.LONG.listOf(), toList(this.mapTargets));
        output.store(TAG_MAP_ORIGINS, Codec.LONG.listOf(), toList(this.mapOrigins));
    }

    private static List<Long> toList(long[] values) {
        List<Long> list = new java.util.ArrayList<>(values.length);
        for (long value : values) {
            list.add(value);
        }
        return list;
    }

    private static void fromList(List<Long> list, long[] into) {
        for (int i = 0; i < into.length && i < list.size(); i++) {
            into[i] = list.get(i);
        }
    }

    @Override
    public void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);

        this.tradeTier =
                input.getIntOr(
                        TAG_TRADE_TIER,
                        1
                );

        this.chainComplete =
                input.getBooleanOr(
                        TAG_CHAIN_COMPLETE,
                        false
                );

        this.firstTradingPlayerId =
                input.read(
                        TAG_FIRST_TRADING_PLAYER,
                        UUIDUtil.CODEC
                ).orElse(null);

        this.tradesInitialized =
                input.getBooleanOr(
                        TAG_TRADES_INITIALIZED,
                        false
                );

        this.uniqueTrader =
                input.getBooleanOr(
                        TAG_UNIQUE,
                        true
                );

        input.read(TAG_MAP_TARGETS, Codec.LONG.listOf())
                .ifPresent(list -> fromList(list, this.mapTargets));
        input.read(TAG_MAP_ORIGINS, Codec.LONG.listOf())
                .ifPresent(list -> fromList(list, this.mapOrigins));

        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTraderTradeDupDebug] readAdditionalSaveData() uuid={} read "
            // + "tradesInitialized={} tradeTier={} stack={}",
            // this.getUUID(),
            // this.tradesInitialized,
            // this.tradeTier,
            // shortStackTrace()
            // );
        }

        // Runtime registries aren't saved, so a guide loaded from disk re-registers
        // itself and, if bonded, restores its persistent reservation.
        if (this.level() instanceof ServerLevel serverLevel) {

            if (this.uniqueTrader
                    && this.firstTradingPlayerId != null) {

                livingTraders(serverLevel).putIfAbsent(
                        this.getType(),
                        this.getUUID()
                );
            }

            // Spawn-egg / summoned guides never take the type's slot, so they can't get
            // the real guide removed as a duplicate.
            if (this.uniqueTrader) {
                registerExistingTrader(serverLevel);
            }
        }
    }

    private static ConcurrentHashMap<EntityType<?>, Long> getTraderRegistry(
            ServerLevel serverLevel) {

        return ACTIVE_TRAIL_TRADERS.computeIfAbsent(
                serverLevel,
                ignored -> new ConcurrentHashMap<>()
        );
    }

    private static ConcurrentHashMap<EntityType<?>, UUID> getTraderIdRegistry(
            ServerLevel serverLevel) {

        return ACTIVE_TRAIL_TRADER_IDS.computeIfAbsent(
                serverLevel,
                ignored -> new ConcurrentHashMap<>()
        );
    }

    /**
     * Registers a loaded unique guide in the runtime registry. Only a bonded
     * guide gets a persistent reservation, so an unbonded one becomes
     * replaceable once its chunk unloads.
     */
    private void registerExistingTrader(
            ServerLevel serverLevel) {

        ConcurrentHashMap<EntityType<?>, Long> registry =
                getTraderRegistry(serverLevel);

        ConcurrentHashMap<EntityType<?>, UUID> idRegistry =
                getTraderIdRegistry(serverLevel);

        if (this.uniqueTrader
                && this.firstTradingPlayerId != null) {

            livingTraders(serverLevel).putIfAbsent(
                    this.getType(),
                    this.getUUID()
            );
        }

        // Never replace an active guide's UUID; a second one is handled by removeIfDuplicate() on its first tick.
        idRegistry.putIfAbsent(
                this.getType(),
                this.getUUID()
        );

        registry.putIfAbsent(
                this.getType(),
                System.currentTimeMillis()
        );
    }

    private void refreshTraderRegistryClaim() {
        if (!this.uniqueTrader
                || !(this.level() instanceof ServerLevel serverLevel)) {

            return;
        }

        ConcurrentHashMap<EntityType<?>, UUID> idRegistry =
                getTraderIdRegistry(serverLevel);

        UUID existing =
                idRegistry.get(this.getType());

        // Only the slot owner refreshes it. A non-owner re-checks once a second: a
        // bonded guide takes the slot (removing an unbonded holder); an unbonded one
        // removes itself if the holder is still loaded, otherwise takes the slot.
        if (existing != null
                && !existing.equals(this.getUUID())) {

            if (this.tickCount % 20 != 0) {
                return;
            }

            Entity holder = serverLevel.getEntity(existing);
            boolean holderAlive = holder instanceof AbstractTrailTraderEntity h
                    && h.isAlive();

            if (this.firstTradingPlayerId != null) {
                if (holderAlive
                        && ((AbstractTrailTraderEntity) holder).firstTradingPlayerId == null) {

                    holder.discard();
                }

                if (holderAlive && holder.isAlive()) {
                    return; // another bonded guide; removeIfDuplicate already logged it
                }

            } else if (holderAlive) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTrader] removing duplicate {} {} at {} - {} {} is the active guide",
                // this.getType(), this.getUUID(), this.blockPosition(),
                // this.getType(), existing);
                this.discard();
                return;
            }

            idRegistry.put(this.getType(), this.getUUID());
            getTraderRegistry(serverLevel).put(this.getType(), System.currentTimeMillis());
            return;
        }

        idRegistry.putIfAbsent(
                this.getType(),
                this.getUUID()
        );

        getTraderRegistry(serverLevel).put(
                this.getType(),
                System.currentTimeMillis()
        );
    }

    /**
     * Releases this trader's runtime uniqueness slot on unload. A bonded guide's
     * persistent reservation in LIVING_TRAIL_TRADERS is unaffected.
     */
    private void releaseTraderRegistryClaim() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ConcurrentHashMap<EntityType<?>, Long> registry =
                ACTIVE_TRAIL_TRADERS.get(serverLevel);

        if (registry != null) {
            registry.remove(
                    this.getType()
            );

            if (registry.isEmpty()) {
                ACTIVE_TRAIL_TRADERS.remove(
                        serverLevel,
                        registry
                );
            }
        }

        ConcurrentHashMap<EntityType<?>, UUID> idRegistry =
                ACTIVE_TRAIL_TRADER_IDS.get(serverLevel);

        if (idRegistry != null) {

            idRegistry.remove(
                    this.getType(),
                    this.getUUID()
            );

            if (idRegistry.isEmpty()) {
                ACTIVE_TRAIL_TRADER_IDS.remove(
                        serverLevel,
                        idRegistry
                );
            }
        }
    }

    /** Keeps the runtime registry heartbeat alive from the entity's own tick (no world scan). */
    private void tickUniquenessRegistry() {
        if (!this.level().isClientSide()
                && this.isAlive()) {

            refreshTraderRegistryClaim();
        }
    }

    /**
     * Persistent per-level record of bonded unique guides; unbonded guides are
     * never recorded, so only a bonded guide reserves its type across unloads.
     */
    private static ConcurrentHashMap<EntityType<?>, UUID> livingTraders(
            ServerLevel serverLevel) {

        return serverLevel.getData(
                ModAttachments.LIVING_TRAIL_TRADERS.get()
        );
    }

    /**
     * First server tick: decides whether this loaded guide stays. A bonded guide
     * wins over unbonded ones, then a ticking slot holder wins, and the newly
     * loaded duplicate is discarded. Returns true if this entity was discarded.
     */
    private boolean removeIfDuplicate(
            ServerLevel serverLevel) {

        if (!this.uniqueTrader) {
            return false;
        }

        ConcurrentHashMap<EntityType<?>, UUID> idRegistry =
                getTraderIdRegistry(serverLevel);

        // 1. A persistently bonded guide always has priority.
        UUID bondedHolder =
                livingTraders(serverLevel).get(
                        this.getType()
                );

        if (bondedHolder != null) {

            if (bondedHolder.equals(this.getUUID())) {

                // This is the bonded guide: evict any unbonded slot holder.
                UUID activeHolder =
                        idRegistry.get(this.getType());

                if (activeHolder != null
                        && !activeHolder.equals(this.getUUID())) {

                    Entity other =
                            serverLevel.getEntity(activeHolder);

                    if (other instanceof AbstractTrailTraderEntity otherTrader
                            && otherTrader.isAlive()) {

                        if (otherTrader.firstTradingPlayerId == null) {
                            otherTrader.discard();
                        } else {
                            // Another bonded guide holds the slot; the persistent record is
                            // authoritative, so this one can't replace it.
                            com.terrainlab.TerrainLab.LOGGER.warn(
                                    "[TrailTrader] conflicting bonded {} {} found "
                                            + "while persistent holder is {}",
                                    this.getType(),
                                    otherTrader.getUUID(),
                                    bondedHolder
                            );

                            this.discard();
                            return true;
                        }
                    }

                    idRegistry.remove(
                            this.getType(),
                            activeHolder
                    );

                    ConcurrentHashMap<EntityType<?>, Long> registry =
                            ACTIVE_TRAIL_TRADERS.get(serverLevel);

                    if (registry != null) {
                        registry.remove(
                                this.getType()
                        );
                    }
                }

                idRegistry.put(
                        this.getType(),
                        this.getUUID()
                );

                getTraderRegistry(serverLevel).put(
                        this.getType(),
                        System.currentTimeMillis()
                );

                return false;
            }

            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTrader] removing duplicate {} {} at {} - "
            // + "bonded guide {} already owns this world's {}",
            // this.getType(),
            // this.getUUID(),
            // this.blockPosition(),
            // bondedHolder,
            // this.getType()
            // );

            this.discard();
            return true;
        }

        // 2. No bonded guide: a ticking slot holder wins.
        UUID activeHolder =
                idRegistry.get(this.getType());

        if (activeHolder != null
                && !activeHolder.equals(this.getUUID())) {

            // Only defer to a holder with a fresh heartbeat; a holder loaded at the edge
            // of view distance never ticks and must not win.
            Entity other = serverLevel.getEntity(activeHolder);
            Long heartbeat = getTraderRegistry(serverLevel).get(this.getType());
            boolean holderTicking = heartbeat != null
                    && System.currentTimeMillis() - heartbeat < SPAWN_CLAIM_TIMEOUT_MS;

            if (holderTicking
                    && other instanceof AbstractTrailTraderEntity
                    && other.isAlive()) {

                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTrader] removing duplicate {} {} at {} - "
                // + "loaded {} {} already occupies the active slot",
                // this.getType(),
                // this.getUUID(),
                // this.blockPosition(),
                // this.getType(),
                // activeHolder
                // );

                this.discard();
                return true;
            }

            if (other instanceof AbstractTrailTraderEntity otherTrader
                    && otherTrader.isAlive()
                    && otherTrader.firstTradingPlayerId == null) {

                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTrader] replacing non-ticking {} {} at {} with {} at {}",
                // this.getType(),
                // otherTrader.getUUID(),
                // otherTrader.blockPosition(),
                // this.getUUID(),
                // this.blockPosition()
                // );

                otherTrader.discard();
            }

            idRegistry.remove(this.getType(), activeHolder);
        }

        // 3. Clear a legacy persistent record that points at this unbonded guide.
        if (this.firstTradingPlayerId == null) {

            livingTraders(serverLevel).remove(
                    this.getType(),
                    this.getUUID()
            );
        }

        idRegistry.put(
                this.getType(),
                this.getUUID()
        );

        getTraderRegistry(serverLevel).put(
                this.getType(),
                System.currentTimeMillis()
        );

        return false;
    }

    /**
     * Always releases the runtime slot. The bonded owner's recall record and the
     * persistent reservation survive a chunk unload but are cleared on any real
     * removal (death, discard).
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        releaseTraderRegistryClaim();

        if (this.uniqueTrader
                && this.firstTradingPlayerId != null
                && this.level() instanceof ServerLevel recallLevel) {

            if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                    || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {

                updateOwnerRecallRecord();
            } else {
                clearOwnerRecallRecord(recallLevel);
            }
        }

        // A real removal (not an unload) frees the persistent reservation.
        if (this.uniqueTrader
                && this.firstTradingPlayerId != null
                && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK
                && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER
                && this.level() instanceof ServerLevel serverLevel) {

            livingTraders(serverLevel).remove(
                    this.getType(),
                    this.getUUID()
            );
        }

        super.remove(reason);
    }

    private static final Map<EntityType<?>, LongAdder>
            GROUND_CHECK_ATTEMPTS =
            new ConcurrentHashMap<>();

    private static final Map<EntityType<?>, LongAdder>
            GROUND_CHECK_PASSES =
            new ConcurrentHashMap<>();

    /** The four guide types, for debug tracing and the /terrainlab guides command. */
    public static java.util.List<EntityType<?>> guideTypes() {
        return java.util.List.of(
                com.terrainlab.registry.ModEntities.RANGER.get(),
                com.terrainlab.registry.ModEntities.ARCHEOLOGIST.get(),
                com.terrainlab.registry.ModEntities.MARINER.get(),
                com.terrainlab.registry.ModEntities.PRIEST.get());
    }

    /** Whether the biome at pos has this type in its spawn list (biome modifiers included). */
    public static boolean biomeListsType(LevelAccessor level, BlockPos pos, EntityType<?> type) {
        return level.getBiome(pos).value().getMobSettings().getMobs(type.getCategory()).unwrap().stream()
                .anyMatch(entry -> entry.value().type() == type);
    }

    public static boolean checkTrailTraderSpawnRules(
            EntityType<? extends AbstractTrailTraderEntity> type,
            LevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random) {

        GROUND_CHECK_ATTEMPTS
                .computeIfAbsent(
                        type,
                        t -> new LongAdder()
                )
                .increment();
        GuideSpawnTrace.record(type, GuideSpawnTrace.Stage.CHECK);

        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTraderSpawnDebug] ATTEMPT type={} pos={} reason={} levelClass={} thread={}",
            // type,
            // pos,
            // reason,
            // level.getClass().getSimpleName(),
            // Thread.currentThread().getName()
            // );
        }

        boolean groundOk =
                level.getBlockState(pos.below())
                        .isValidSpawn(
                                level,
                                pos.below(),
                                type
                        );

        if (!groundOk) {
            GuideSpawnTrace.record(type, GuideSpawnTrace.Stage.GROUND);

            if (DEBUG_LOGGING) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTraderSpawnDebug] GROUND FAIL type={} pos={} belowBlock={} reason={}",
                // type,
                // pos,
                // level.getBlockState(pos.below()).getBlock(),
                // reason
                // );
            }

            return false;
        }

        GROUND_CHECK_PASSES
                .computeIfAbsent(
                        type,
                        t -> new LongAdder()
                )
                .increment();

        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTraderSpawnDebug] GROUND PASS type={} pos={} belowBlock={} reason={}",
            // type,
            // pos,
            // level.getBlockState(pos.below()).getBlock(),
            // reason
            // );
        }

        // World-generation spawning picks the spawn list from the biome at one corner of the chunk
        // but places the mob anywhere in it, so a guide could land in the biome next door (beaches
        // are only a few blocks wide). Require the spot itself to list this guide.
        if (!biomeListsType(level, pos, type)) {
            GuideSpawnTrace.record(type, GuideSpawnTrace.Stage.BIOME);
            return false;
        }

        // Never spawn near a guide destination.
        if (GuideStructureAvoidance.isNearDestination(level, pos)) {
            GuideSpawnTrace.record(type, GuideSpawnTrace.Stage.NEAR_DESTINATION);
            return false;
        }

        if (!(level instanceof ServerLevelAccessor serverLevelAccessor)) {

            if (DEBUG_LOGGING) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTraderSpawnDebug] PASS type={} pos={} - "
                // + "not ServerLevelAccessor, skipping uniqueness check",
                // type,
                // pos
                // );
            }

            GuideSpawnTrace.record(type, GuideSpawnTrace.Stage.PASS);
            return true;
        }

        ServerLevel serverLevel =
                serverLevelAccessor.getLevel();

        boolean unique =
                passesUniqueTraderCheck(
                        type,
                        serverLevel,
                        pos
                );

        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTraderSpawnDebug] FINAL PREDICATE RESULT type={} pos={} -> {}",
            // type,
            // pos,
            // unique ? "PASS" : "FAIL"
            // );
        }

        if (unique) {
            GuideSpawnTrace.record(type, GuideSpawnTrace.Stage.PASS);
        }
        return unique;
    }

    /**
     * Thread-safe uniqueness check for the spawn predicate; never queries entity
     * section storage. Fails if a bonded guide of the type is persistently
     * recorded, otherwise atomically claims the runtime slot unless a live claim
     * or loaded guide already holds it.
     */
    private static boolean passesUniqueTraderCheck(
            EntityType<?> type,
            ServerLevel serverLevel,
            BlockPos pos) {

        // A bonded guide of this type exists, possibly in an unloaded chunk.
        if (livingTraders(serverLevel).containsKey(type)) {
            GuideSpawnTrace.record(type, GuideSpawnTrace.Stage.BONDED_EXISTS);
            if (DEBUG_LOGGING) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTraderSpawnDebug] type={} pos={} -> FAIL "
                // + "(persistent bonded guide already exists)",
                // type,
                // pos
                // );
            }

            return false;
        }

        ConcurrentHashMap<EntityType<?>, Long> registry =
                getTraderRegistry(serverLevel);

        long now = System.currentTimeMillis();

        while (true) {
            Long existing =
                    registry.putIfAbsent(
                            type,
                            now
                    );

            if (existing == null) {

                if (DEBUG_LOGGING) {
                    // com.terrainlab.TerrainLab.LOGGER.info(
                    // "[TrailTraderSpawnDebug] type={} pos={} thread={} -> PASS "
                    // + "at ground-check stage "
                    // + "(loaded-guide/spawn claim acquired)",
                    // type,
                    // pos,
                    // Thread.currentThread().getName()
                    // );
                }

                return true;
            }

            long age = now - existing;

            if (age < SPAWN_CLAIM_TIMEOUT_MS) {
                GuideSpawnTrace.record(type, GuideSpawnTrace.Stage.CLAIM_ACTIVE);

                if (DEBUG_LOGGING) {
                    // com.terrainlab.TerrainLab.LOGGER.info(
                    // "[TrailTraderSpawnDebug] type={} pos={} thread={} -> FAIL "
                    // + "at ground-check stage "
                    // + "(another loaded guide/spawn claim is active; "
                    // + "age={}ms)",
                    // type,
                    // pos,
                    // Thread.currentThread().getName(),
                    // age
                    // );
                }

                return false;
            }

            // Stale claim: replace it only if it is still the value observed.
            if (registry.replace(
                    type,
                    existing,
                    now
            )) {

                if (DEBUG_LOGGING) {
                    // com.terrainlab.TerrainLab.LOGGER.info(
                    // "[TrailTraderSpawnDebug] type={} pos={} thread={} -> PASS "
                    // + "at ground-check stage "
                    // + "(replaced stale claim age={}ms)",
                    // type,
                    // pos,
                    // Thread.currentThread().getName(),
                    // age
                    // );
                }

                return true;
            }
        }
    }

    public static Map<EntityType<?>, Long> drainGroundCheckAttempts() {
        Map<EntityType<?>, Long> snapshot =
                new HashMap<>();

        GROUND_CHECK_ATTEMPTS.forEach(
                (type, adder) ->
                        snapshot.put(
                                type,
                                adder.sumThenReset()
                        )
        );

        return snapshot;
    }

    public static Map<EntityType<?>, Long> drainGroundCheckPasses() {
        Map<EntityType<?>, Long> snapshot =
                new HashMap<>();

        GROUND_CHECK_PASSES.forEach(
                (type, adder) ->
                        snapshot.put(
                                type,
                                adder.sumThenReset()
                        )
        );

        return snapshot;
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor levelAccessor,
            DifficultyInstance difficulty,
            EntitySpawnReason spawnReason,
            @Nullable SpawnGroupData spawnGroupData) {

        SpawnGroupData result =
                super.finalizeSpawn(
                        levelAccessor,
                        difficulty,
                        spawnReason,
                        spawnGroupData
                );

        if (levelAccessor.getLevel()
                instanceof ServerLevel serverLevel) {

            // Only natural / worldgen spawns are the type's unique guide; eggs and commands are not.
            this.uniqueTrader =
                    spawnReason == EntitySpawnReason.NATURAL
                            || spawnReason == EntitySpawnReason.CHUNK_GENERATION;

            // Bind the slot claimed by the spawn predicate to this entity. If another
            // loaded guide took it meanwhile, removeIfDuplicate() removes this one on its
            // first tick.
            if (this.uniqueTrader) {
                getTraderIdRegistry(serverLevel).putIfAbsent(
                        this.getType(),
                        this.getUUID()
                );

                // Chunk-generation spawns run on a worldgen thread, where other entities must not be
                // looked up or removed; there the claim above is all that is recorded, and the guide's
                // first tick (removeIfDuplicate) settles any conflict on the server thread.
                if (serverLevel.getServer().isSameThread()) {
                    refreshTraderRegistryClaim();
                }
            }

            // Only a bond creates the persistent reservation; normally false here.
            if (this.uniqueTrader
                    && this.firstTradingPlayerId != null) {

                livingTraders(serverLevel).putIfAbsent(
                        this.getType(),
                        this.getUUID()
                );
            }

            if (DEBUG_LOGGING) {
                // com.terrainlab.TerrainLab.LOGGER.info(
                // "[TrailTraderDonkeyDebug] finalizeSpawn deferring "
                // + "spawnCargoDonkey for {} (uuid={}) "
                // + "to next main-thread tick",
                // this.getType(),
                // this.getUUID()
                // );
            }

            serverLevel.getServer().execute(() -> {
                if (this.isAlive()) {
                    spawnCargoDonkey(
                            serverLevel,
                            difficulty
                    );
                } else if (DEBUG_LOGGING) {
                    // com.terrainlab.TerrainLab.LOGGER.info(
                    // "[TrailTraderDonkeyDebug] deferred tick ran "
                    // + "but trader {} is no longer alive "
                    // + "- skipping donkey spawn",
                    // this.getUUID()
                    // );
                }
            });
        }

        return result;
    }

    private void spawnCargoDonkey(
            ServerLevel serverLevel,
            DifficultyInstance difficulty) {

        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTraderDonkeyDebug] spawnCargoDonkey running "
            // + "for trader {} (uuid={}) at {}",
            // this.getType(),
            // this.getUUID(),
            // this.blockPosition()
            // );
        }

        Donkey donkey =
                EntityType.DONKEY.create(
                        serverLevel,
                        EntitySpawnReason.MOB_SUMMONED
                );

        if (donkey == null) {
            com.terrainlab.TerrainLab.LOGGER.warn(
                    "[TrailTraderDonkeyDebug] EntityType.DONKEY.create() "
                            + "returned null - no donkey/lead will spawn "
                            + "for trader {}",
                    this.getUUID()
            );

            return;
        }

        double leftYawRad =
                Math.toRadians(
                        this.getYRot() - 90.0
                );

        double offsetX =
                -Math.sin(leftYawRad) * 2.0;

        double offsetZ =
                Math.cos(leftYawRad) * 2.0;

        donkey.setPos(
                this.getX() + offsetX,
                this.getY(),
                this.getZ() + offsetZ
        );

        donkey.setYRot(
                this.getYRot()
        );

        donkey.setYHeadRot(
                this.getYRot()
        );

        donkey.finalizeSpawn(
                serverLevel,
                difficulty,
                EntitySpawnReason.MOB_SUMMONED,
                null
        );

        SlotAccess chestSlot =
                donkey.getSlot(499);

        if (chestSlot != null) {
            chestSlot.set(
                    new ItemStack(Items.CHEST)
            );
        }

        donkey.setPersistenceRequired();
        donkey.setTamed(false);

        populateDonkeyCargo(
                serverLevel,
                donkey
        );

        boolean addedToLevel =
                serverLevel.addFreshEntity(donkey);

        donkey.setLeashedTo(
                this,
                true
        );

        donkey.setData(
                ModAttachments.CARGO_DONKEY_OWNER_TRADER.get(),
                Optional.of(this.getUUID())
        );

        Object leashHolderAfter =
                donkey.getLeashHolder();

        if (DEBUG_LOGGING) {
            // com.terrainlab.TerrainLab.LOGGER.info(
            // "[TrailTraderDonkeyDebug] donkey {} spawned near trader {} "
            // + "- addedToLevel={}, leashHolderAfterSetLeashedTo={} "
            // + "(expected: this trader, uuid={})",
            // donkey.getUUID(),
            // this.getUUID(),
            // addedToLevel,
            // leashHolderAfter,
            // this.getUUID()
            // );
        }
    }

    private void populateDonkeyCargo(
            ServerLevel serverLevel,
            Donkey donkey) {

        ResourceKey<LootTable> lootTableKey =
                getDonkeyCargoLootTable();

        LootTable lootTable =
                serverLevel.getServer()
                        .reloadableRegistries()
                        .getLootTable(lootTableKey);

        LootParams params =
                new LootParams.Builder(serverLevel)
                        .withParameter(
                                LootContextParams.ORIGIN,
                                donkey.position()
                        )
                        .create(
                                LootContextParamSets.CHEST
                        );

        List<ItemStack> cargo =
                lootTable.getRandomItems(
                        params,
                        RandomSource.create()
                );

        int containerSize =
                donkey.getInventory()
                        .getContainerSize();

        if (containerSize <= 2) {
            return;
        }

        int slot = 2;

        for (ItemStack stack : cargo) {
            if (slot >= containerSize) {
                break;
            }

            donkey.getInventory().setItem(
                    slot,
                    stack
            );

            slot++;
        }
    }


    /**
     * Bonded guides survive lethal damage by escaping to a safe location
     * approximately 100 blocks from where the lethal damage occurred.
     * Unbonded guides retain normal vanilla death behaviour.
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource damageSource) {
        if (this.firstTradingPlayerId == null
                || !(this.level() instanceof ServerLevel serverLevel)) {
            super.die(damageSource);
            return;
        }

        // Restore health before escaping so vanilla death processing never
        // removes the bonded guide or clears its persistent identity.
        this.setHealth(this.getMaxHealth());

        double originX = this.getX();
        double originY = this.getY();
        double originZ = this.getZ();

        RandomSource random = this.getRandom();

        // Search around the original position, aiming for roughly 100 blocks away.
        for (int attempt = 0; attempt < 64; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 90.0D + random.nextDouble() * 20.0D;

            double targetX = originX + Math.cos(angle) * distance;
            double targetZ = originZ + Math.sin(angle) * distance;

            int baseY = BlockPos.containing(originX, originY, originZ).getY();

            for (int verticalOffset = 0; verticalOffset <= 16; verticalOffset++) {
                if (tryTeleportToBondPosition(
                        serverLevel, targetX, baseY + verticalOffset, targetZ)) {
                    this.setHealth(this.getMaxHealth());
                    this.setDeltaMovement(0.0D, 0.0D, 0.0D);
                    return;
                }

                if (verticalOffset > 0
                        && tryTeleportToBondPosition(
                        serverLevel, targetX, baseY - verticalOffset, targetZ)) {
                    this.setHealth(this.getMaxHealth());
                    this.setDeltaMovement(0.0D, 0.0D, 0.0D);
                    return;
                }
            }
        }

        // No safe spot near where it fell (for example in the void, where there is no ground at
        // all): go to the bonded player if they are in this dimension, else to the world spawn.
        // Never call super.die(): that would remove the guide and lose its identity.
        this.setHealth(this.getMaxHealth());
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        this.resetFallDistance();

        Player owner = serverLevel.getPlayerByUUID(this.firstTradingPlayerId);
        if (owner != null && !owner.isSpectator()) {
            this.teleportTo(owner.getX(), owner.getY(), owner.getZ());
        } else {
            ServerLevel overworld = serverLevel.getServer().overworld();
            BlockPos spawn = overworld.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    overworld.getRespawnData().pos());
            if (overworld == serverLevel) {
                this.teleportTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
            } else {
                this.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                        java.util.Set.of(), this.getYRot(), this.getXRot(), true);
            }
        }
        this.getNavigation().stop();
    }

    /** Cargo donkeys travel with the guide so the lead never snaps and drops a spare lead. */
    @Override
    public void teleportTo(double x, double y, double z) {
        super.teleportTo(x, y, z);
        if (this.level() instanceof ServerLevel serverLevel) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof Donkey donkey
                        && donkey.getData(ModAttachments.CARGO_DONKEY_OWNER_TRADER.get())
                        .filter(this.getUUID()::equals).isPresent()) {
                    donkey.teleportTo(x, y, z);
                }
            }
        }
    }

    /** Beyond this a cargo donkey is moved back to its guide (vanilla leads snap at 12 blocks). */
    public static final double CARGO_DONKEY_MAX_DISTANCE_SQR = 10.0 * 10.0;

    public static void tickCargoDonkeyLeashWatchdog(
            ServerLevel level) {

        // Bonded-guide recall shares this ~5 s cadence.
        tickBondedGuideRecall(level);

        // Iterate loaded entities directly; an AABB query over the whole world walks every entity section.
        java.util.List<Donkey> donkeys =
                new java.util.ArrayList<>();

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Donkey d
                    && d.getType() == EntityType.DONKEY
                    && d.isAlive()) {

                donkeys.add(d);
            }
        }

        for (Donkey donkey : donkeys) {

            Optional<UUID> ownerId =
                    donkey.getData(
                            ModAttachments.CARGO_DONKEY_OWNER_TRADER.get()
                    );

            if (ownerId.isEmpty()) {
                continue;
            }

            Entity owner =
                    level.getEntity(
                            ownerId.get()
                    );

            if (!(owner instanceof AbstractTrailTraderEntity trader)
                    || !trader.isAlive()) {

                continue;
            }

            // Out of lead range: bring the donkey over, even if it is still on the lead (a donkey
            // left behind in an unloaded chunk reattaches by ID from any distance, and a
            // non-ticking one never snaps, so the lead is drawn across the whole gap).
            if (donkey.distanceToSqr(trader) > CARGO_DONKEY_MAX_DISTANCE_SQR) {
                donkey.teleportTo(trader.getX(), trader.getY(), trader.getZ());
            }

            if (donkey.getLeashHolder() != trader) {
                donkey.setLeashedTo(
                        trader,
                        true
                );

            }
        }
    }
}