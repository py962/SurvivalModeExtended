package com.terrainlab.registry;

import com.terrainlab.TerrainLab;
import com.terrainlab.attachment.HorseSprintConfig;
import com.terrainlab.village.VillagerThermalState;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Data attachment types for players, mobs, chunks, levels and block entities. Unless noted, attachments are transient (not serialized). */
public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TerrainLab.MODID);

    public static final Supplier<AttachmentType<Float>> HORSE_STAMINA =
            ATTACHMENT_TYPES.register("horse_stamina", () ->
                    AttachmentType.builder(() -> HorseSprintConfig.MAX_STAMINA)
                            .serialize(Codec.FLOAT.fieldOf("horse_stamina"))
                            .build());

    public static final Supplier<AttachmentType<Boolean>> HORSE_EXHAUSTED =
            ATTACHMENT_TYPES.register("horse_exhausted", () ->
                    AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL.fieldOf("horse_exhausted"))
                            .build());

    // Per-chunk cache of blended (pre-altitude) seasonal biome profiles, one per column; recomputable.
    public static final Supplier<AttachmentType<com.terrainlab.temperature.ChunkTemperatureCache>> CHUNK_TEMPERATURE_CACHE =
            ATTACHMENT_TYPES.register("chunk_temperature_cache", () ->
                    AttachmentType.builder(com.terrainlab.temperature.ChunkTemperatureCache::new).build());

    // Whether this horse's base speed has been scaled into trot range (set once, preserving bred variance).
    public static final Supplier<AttachmentType<Boolean>> HORSE_SPEED_INITIALIZED =
            ATTACHMENT_TYPES.register("horse_speed_initialized", () ->
                    AttachmentType.builder(() -> false)
                            .serialize(Codec.BOOL.fieldOf("horse_speed_initialized"))
                            .build());

    // Energy: the activity resource that replaces vanilla saturation.
    public static final Supplier<AttachmentType<Float>> PLAYER_ENERGY =
            ATTACHMENT_TYPES.register("player_energy", () ->
                    AttachmentType.builder(() -> com.terrainlab.energy.PlayerEnergyConstants.SPAWN_ENERGY)
                            .serialize(Codec.FLOAT.fieldOf("player_energy"))
                            .build());

    // Dual wielding: off-hand attack ticker and last-seen hand stacks. Both logical sides; transient.
    public static final Supplier<AttachmentType<com.terrainlab.combat.DualWield.State>> DUAL_WIELD =
            ATTACHMENT_TYPES.register("dual_wield", () ->
                    AttachmentType.builder(com.terrainlab.combat.DualWield.State::new).build());

    // Raw client-reported jump/attack key state.
    public static final Supplier<AttachmentType<Boolean>> PLAYER_WANTS_JUMP =
            ATTACHMENT_TYPES.register("player_wants_jump", () ->
                    AttachmentType.builder(() -> false).build());

    public static final Supplier<AttachmentType<Boolean>> PLAYER_WANTS_ATTACK =
            ATTACHMENT_TYPES.register("player_wants_attack", () ->
                    AttachmentType.builder(() -> false).build());

    // Ticks the attack/mine key has been held continuously; drives drain-while-held.
    public static final Supplier<AttachmentType<Integer>> PLAYER_ATTACK_HELD_TICKS =
            ATTACHMENT_TYPES.register("player_attack_held_ticks", () ->
                    AttachmentType.builder(() -> 0).build());

    // Ticks since the last hunger-point conversion while asleep; reset on waking.
    public static final Supplier<AttachmentType<Integer>> PLAYER_REST_TICK_COUNTER =
            ATTACHMENT_TYPES.register("player_rest_tick_counter", () ->
                    AttachmentType.builder(() -> 0).build());

    // Day time when sleep started, read on wake to compute how much night was slept; -1 = not sleeping.
    public static final Supplier<AttachmentType<Long>> PLAYER_SLEEP_START_DAYTIME =
            ATTACHMENT_TYPES.register("player_sleep_start_daytime", () ->
                    AttachmentType.builder(() -> -1L).build());

    // Ticks since the last flat-rate hunger drain.
    public static final Supplier<AttachmentType<Integer>> PLAYER_HUNGER_DRAIN_COUNTER =
            ATTACHMENT_TYPES.register("player_hunger_drain_counter", () ->
                    AttachmentType.builder(() -> 0)
                            .serialize(Codec.INT.fieldOf("player_hunger_drain_counter"))
                            .build());

    // Peaceful-difficulty starvation damage timer (80-tick cadence, as in vanilla FoodData).
    public static final Supplier<AttachmentType<Integer>> PLAYER_PEACEFUL_STARVE_TIMER =
            ATTACHMENT_TYPES.register("player_peaceful_starve_timer", () ->
                    AttachmentType.builder(() -> 0).build());

    // Hunger before eating began, so the finish handler can undo exactly what vanilla added (20-cap aware).
    public static final Supplier<AttachmentType<Integer>> PLAYER_PRE_EAT_HUNGER =
            ATTACHMENT_TYPES.register("player_pre_eat_hunger", () ->
                    AttachmentType.builder(() -> 0).build());

    // Saturation before eating began; restored alongside PLAYER_PRE_EAT_HUNGER.
    public static final Supplier<AttachmentType<Float>> PLAYER_PRE_EAT_SATURATION =
            ATTACHMENT_TYPES.register("player_pre_eat_saturation", () ->
                    AttachmentType.builder(() -> 0.0F).build());

    // Queue of delayed heals (slow drip); overlapping heals stack additively.
    public static final Supplier<AttachmentType<java.util.List<com.terrainlab.food.PendingHeal>>> PLAYER_PENDING_HEALS =
            ATTACHMENT_TYPES.register("player_pending_heals", () ->
                    AttachmentType.<java.util.List<com.terrainlab.food.PendingHeal>>builder(() -> new java.util.ArrayList<>()).build());

    // Energy-food daily cap: uses today and the day they were counted on.
    public static final Supplier<AttachmentType<Integer>> PLAYER_ENERGY_FOOD_USES_TODAY =
            ATTACHMENT_TYPES.register("player_energy_food_uses_today", () ->
                    AttachmentType.builder(() -> 0)
                            .serialize(Codec.INT.fieldOf("player_energy_food_uses_today"))
                            .copyOnDeath()
                            .build());

    public static final Supplier<AttachmentType<Long>> PLAYER_ENERGY_FOOD_LAST_DAY =
            ATTACHMENT_TYPES.register("player_energy_food_last_day", () ->
                    AttachmentType.builder(() -> -1L)
                            .serialize(Codec.LONG.fieldOf("player_energy_food_last_day"))
                            .copyOnDeath()
                            .build());

    // Previous-tick jump key state for rising-edge detection.
    public static final Supplier<AttachmentType<Boolean>> PLAYER_JUMP_WAS_DOWN =
            ATTACHMENT_TYPES.register("player_jump_was_down", () ->
                    AttachmentType.builder(() -> false).build());

    // Whether the player was on the ground last tick (jump detection).
    public static final Supplier<AttachmentType<Boolean>> PLAYER_WAS_ON_GROUND =
            ATTACHMENT_TYPES.register("player_was_on_ground", () ->
                    AttachmentType.builder(() -> true).build());

    // Remaining simulated furnace catch-up ticks; dropped if the server restarts mid catch-up.
    public static final Supplier<AttachmentType<Integer>> FURNACE_PENDING_CATCHUP =
            ATTACHMENT_TYPES.register("furnace_pending_catchup", () ->
                    AttachmentType.builder(() -> 0).build());

    // Player internal temperature. Serialized.
    public static final Supplier<AttachmentType<Float>> PLAYER_INTERNAL_TEMP =
            ATTACHMENT_TYPES.register("player_internal_temp", () ->
                    AttachmentType.builder(() -> (float) com.terrainlab.temperature.PlayerTemperatureConstants.COMFORTABLE_RESTING_VALUE)
                            .serialize(Codec.FLOAT.fieldOf("player_internal_temp"))
                            .build());

    // Raw client-reported use-key state (see WaterDrinkInputPacket).
    public static final Supplier<AttachmentType<Boolean>> PLAYER_WANTS_WATER_DRINK =
            ATTACHMENT_TYPES.register("player_wants_water_drink", () ->
                    AttachmentType.builder(() -> false).build());

    // Hold/cooldown state machine for drinking water (see WaterDrinkState).
    public static final Supplier<AttachmentType<com.terrainlab.temperature.WaterDrinkState>> WATER_DRINK_STATE =
            ATTACHMENT_TYPES.register("water_drink_state", () ->
                    AttachmentType.builder(com.terrainlab.temperature.WaterDrinkState::new).build());

    // Player wetness; server-authoritative since it feeds temperature damage. Starts dry after a relog.
    public static final Supplier<AttachmentType<Float>> PLAYER_WETNESS =
            ATTACHMENT_TYPES.register("player_wetness", () ->
                    AttachmentType.builder(() -> 0.0F).build());

    // Heat-damage accumulator: health loss is applied each time it crosses the threshold.
    public static final Supplier<AttachmentType<Float>> PLAYER_HEAT_DAMAGE_ACCUMULATOR =
            ATTACHMENT_TYPES.register("player_heat_damage_accumulator", () ->
                    AttachmentType.builder(() -> 0.0F).build());

    // Level-scoped index of heat sources; recomputable.
    public static final Supplier<AttachmentType<com.terrainlab.temperature.HeatSourceIndex>> HEAT_SOURCE_INDEX =
            ATTACHMENT_TYPES.register("heat_source_index", () ->
                    AttachmentType.builder(com.terrainlab.temperature.HeatSourceIndex::new).build());

    // Per-chunk positions (BlockPos#asLong) of lava placed by players, so it is re-indexed as a
    // heat source when the chunk loads again. Serialized.
    public static final Supplier<AttachmentType<List<Long>>> PLACED_LAVA =
            ATTACHMENT_TYPES.register("placed_lava", () ->
                    AttachmentType.<List<Long>>builder(() -> List.of())
                            .serialize(Codec.LONG.listOf().fieldOf("placed_lava"))
                            .build());

    // Per-chunk sky exposure fractions, one per column; invalidated by nearby block changes.
    public static final Supplier<AttachmentType<com.terrainlab.temperature.ChunkSkyExposureCache>> CHUNK_SKY_EXPOSURE_CACHE =
            ATTACHMENT_TYPES.register("chunk_sky_exposure_cache", () ->
                    AttachmentType.builder(com.terrainlab.temperature.ChunkSkyExposureCache::new).build());

    // Sprint key state while mounted (see HorseSprintInputPacket).
    public static final Supplier<AttachmentType<Boolean>> PLAYER_WANTS_HORSE_SPRINT =
            ATTACHMENT_TYPES.register("player_wants_horse_sprint", () ->
                    AttachmentType.builder(() -> false).build());

    // Game time this goat/caribou was last sheared, for the regrowth cooldown; -1 = never. Serialized.
    public static final Supplier<AttachmentType<Long>> LAST_SHEARED_TIME =
            ATTACHMENT_TYPES.register("last_sheared_time", () ->
                    AttachmentType.builder(() -> -1L)
                            .serialize(Codec.LONG.fieldOf("last_sheared_time"))
                            .sync(ByteBufCodecs.VAR_LONG)
                            .build());

    // Tamed wolf/cat anchor bed, claimed the first time the owner sleeps after taming or a reset. Serialized.
    public static final Supplier<AttachmentType<Optional<BlockPos>>> ANCHOR_BED_POS =
            ATTACHMENT_TYPES.register("anchor_bed_pos", () ->
                    AttachmentType.builder(Optional::<BlockPos>empty)
                            .serialize(BlockPos.CODEC.optionalFieldOf("pos"))
                            .build());

    // Owner of a camel, elephant, strider or happy ghast (the player who fitted its saddle/howdah/harness).
    // Read by GoatHornSummonHandler. Serialized; empty = unowned.
    public static final Supplier<AttachmentType<Optional<UUID>>> MOUNT_OWNER =
            ATTACHMENT_TYPES.register("mount_owner", () ->
                    AttachmentType.builder(Optional::<UUID>empty)
                            .serialize(UUIDUtil.CODEC.optionalFieldOf("mount_owner"))
                            .build());

    // Villager thermal state (cold/hot/comfortable); recomputed on the next staggered read.
    public static final Supplier<AttachmentType<VillagerThermalState>> VILLAGER_THERMAL_STATE =
            ATTACHMENT_TYPES.register("villager_thermal_state", () ->
                    AttachmentType.builder(() -> VillagerThermalState.COMFORTABLE).build());

    // Villager wetness, ticked on each staggered read.
    public static final Supplier<AttachmentType<Float>> VILLAGER_WETNESS =
            ATTACHMENT_TYPES.register("villager_wetness", () ->
                    AttachmentType.builder(() -> 0.0F).build());

    // Smoothed villager temperature compared against the cold/hot cut-offs; NaN until first read.
    public static final Supplier<AttachmentType<Float>> VILLAGER_FELT_TEMP =
            ATTACHMENT_TYPES.register("villager_felt_temp", () ->
                    AttachmentType.builder(() -> Float.NaN).build());

    // Game time of the villager's last panic trigger (see VillagerFearService); -1 = never.
    public static final Supplier<AttachmentType<Long>> VILLAGER_LAST_PANIC_TICK =
            ATTACHMENT_TYPES.register("villager_last_panic_tick", () ->
                    AttachmentType.builder(() -> -1L).build());

    // Game time of the villager's last completed sleep (see VillagerTiredService); -1 = never.
    public static final Supplier<AttachmentType<Long>> VILLAGER_LAST_SLEPT_TICK =
            ATTACHMENT_TYPES.register("villager_last_slept_tick", () ->
                    AttachmentType.builder(() -> -1L).build());

    // Previous-read sleeping state, for falling-edge detection.
    public static final Supplier<AttachmentType<Boolean>> VILLAGER_WAS_SLEEPING =
            ATTACHMENT_TYPES.register("villager_was_sleeping", () ->
                    AttachmentType.builder(() -> false).build());

    // Trapped detection (VillagerTrappedService): position at the start of the current day.
    public static final Supplier<AttachmentType<BlockPos>> VILLAGER_WANDER_DAY_START_POS =
            ATTACHMENT_TYPES.register("villager_wander_day_start_pos", () ->
                    AttachmentType.builder(() -> BlockPos.ZERO).build());

    // Trapped detection: max horizontal distance (blocks) reached from the day-start position today.
    public static final Supplier<AttachmentType<Float>> VILLAGER_WANDER_MAX_DIST =
            ATTACHMENT_TYPES.register("villager_wander_max_dist", () ->
                    AttachmentType.builder(() -> 0.0F).build());

    // Trapped detection: day number the wander tracking was last reset for; -1 = never.
    public static final Supplier<AttachmentType<Long>> VILLAGER_WANDER_LAST_DAY =
            ATTACHMENT_TYPES.register("villager_wander_last_day", () ->
                    AttachmentType.builder(() -> -1L).build());

    // Trapped detection: consecutive trapped days; must reach TRAPPED_CONFIRM_STREAK_DAYS before IS_TRAPPED flips.
    public static final Supplier<AttachmentType<Integer>> VILLAGER_TRAPPED_STREAK_DAYS =
            ATTACHMENT_TYPES.register("villager_trapped_streak_days", () ->
                    AttachmentType.builder(() -> 0).build());

    // Trapped detection: whether the villager is currently considered trapped.
    public static final Supplier<AttachmentType<Boolean>> VILLAGER_IS_TRAPPED =
            ATTACHMENT_TYPES.register("villager_is_trapped", () ->
                    AttachmentType.builder(() -> false).build());

    // Peace amulet: a mob struck while peace protection applies reverts to normal aggro for life.
    public static final Supplier<AttachmentType<Boolean>> PEACE_PROVOKED =
            ATTACHMENT_TYPES.register("peace_provoked", () ->
                    AttachmentType.builder(() -> false).build());

    // Luck/End amulet "worn and paired conduit active" flags, refreshed on the staggered player tick
    // and read by hot-path enchantment/effect hooks to avoid repeated Curios lookups.
    public static final Supplier<AttachmentType<Boolean>> LUCK_AMULET_ACTIVE =
            ATTACHMENT_TYPES.register("luck_amulet_active", () ->
                    AttachmentType.builder(() -> false).build());

    public static final Supplier<AttachmentType<Boolean>> END_AMULET_ACTIVE =
            ATTACHMENT_TYPES.register("end_amulet_active", () ->
                    AttachmentType.builder(() -> false).build());

    // Luck/Peace/End conduit proximity flags: true while within an active conduit's radius, with or
    // without an amulet. OR'd with the matching *_AMULET_ACTIVE flag where the effect is applied.
    public static final Supplier<AttachmentType<Boolean>> LUCK_CONDUIT_NEARBY =
            ATTACHMENT_TYPES.register("luck_conduit_nearby", () ->
                    AttachmentType.builder(() -> false).build());

    public static final Supplier<AttachmentType<Boolean>> PEACE_CONDUIT_NEARBY =
            ATTACHMENT_TYPES.register("peace_conduit_nearby", () ->
                    AttachmentType.builder(() -> false).build());

    public static final Supplier<AttachmentType<Boolean>> END_CONDUIT_NEARBY =
            ATTACHMENT_TYPES.register("end_conduit_nearby", () ->
                    AttachmentType.builder(() -> false).build());

    // Marks an XP orb already handled by CelestialLuckXpHandler (original and bonus twin) so it isn't doubled again.
    public static final Supplier<AttachmentType<Boolean>> LUCK_ORB_BOOSTED =
            ATTACHMENT_TYPES.register("luck_orb_boosted", () ->
                    AttachmentType.builder(() -> false).build());

    // Level-scoped claims making the "one Trail Trader of each type" check atomic across worldgen
    // spawner threads. Value is a wall-clock ms timestamp; claims older than CLAIM_EXPIRY_MS are stale.
    public static final Supplier<AttachmentType<ConcurrentHashMap<EntityType<?>, Long>>> TRAIL_TRADER_SPAWN_CLAIMS =
            ATTACHMENT_TYPES.register("trail_trader_spawn_claims", () ->
                    // Cast picks the Supplier overload of AttachmentType.builder.
                    AttachmentType.<ConcurrentHashMap<EntityType<?>, Long>>builder(
                            (Supplier<ConcurrentHashMap<EntityType<?>, Long>>) ConcurrentHashMap::new).build());

    // The Trail Trader variant this world is bonded to once any guide completes its first trade; other
    // variants then stop spawning naturally. Level-scoped. Serialized; empty = not yet bonded.
    public static final Supplier<AttachmentType<Optional<EntityType<?>>>> BONDED_TRAIL_TRADER_TYPE =
            ATTACHMENT_TYPES.register("bonded_trail_trader_type", () ->
                    AttachmentType.builder(Optional::<EntityType<?>>empty)
                            .serialize(BuiltInRegistries.ENTITY_TYPE.byNameCodec()
                                    .optionalFieldOf("bonded_trail_trader_type"))
                            .build());

    // Trader type -> UUID of the natural trader holding that type's single slot. Cleared when the trader
    // dies, despawns or changes dimension, not on chunk unload. Concurrent because the spawn predicate
    // runs on worldgen threads. Serialized so unloaded traders still hold their slot after a restart.
    public static final Supplier<AttachmentType<ConcurrentHashMap<EntityType<?>, UUID>>> LIVING_TRAIL_TRADERS =
            ATTACHMENT_TYPES.register("living_trail_traders", () ->
                    AttachmentType.<ConcurrentHashMap<EntityType<?>, UUID>>builder(
                                    (Supplier<ConcurrentHashMap<EntityType<?>, UUID>>) ConcurrentHashMap::new)
                            .serialize(Codec.unboundedMap(BuiltInRegistries.ENTITY_TYPE.byNameCodec(), UUIDUtil.CODEC)
                                    .xmap(
                                            (Map<EntityType<?>, UUID> map) -> new ConcurrentHashMap<EntityType<?>, UUID>(map),
                                            (ConcurrentHashMap<EntityType<?>, UUID> map) -> (Map<EntityType<?>, UUID>) map)
                                    .fieldOf("traders"))
                            .build());

    // On a cargo donkey: UUID of the trader it belongs to, used by the leash watchdog. Serialized.
    public static final Supplier<AttachmentType<Optional<UUID>>> CARGO_DONKEY_OWNER_TRADER =
            ATTACHMENT_TYPES.register("cargo_donkey_owner_trader", () ->
                    AttachmentType.builder(Optional::<UUID>empty)
                            .serialize(UUIDUtil.CODEC.optionalFieldOf("cargo_donkey_owner_trader"))
                            .build());

    // Game time the player was last penalised for wearing the sentry trim (global cooldown, see
    // SentryTrimReputationHandler); -1 = never.
    public static final Supplier<AttachmentType<Long>> PLAYER_LAST_SENTRY_TRIM_PENALTY_TICK =
            ATTACHMENT_TYPES.register("player_last_sentry_trim_penalty_tick", () ->
                    AttachmentType.builder(() -> -1L).build());

    // Villager discomfort bitmask (VillagerMoodService flags), recomputed on the staggered read and synced
    // to clients for mood particles and body language. Only set when it changes.
    public static final Supplier<AttachmentType<Integer>> VILLAGER_MOOD_FLAGS =
            ATTACHMENT_TYPES.register("villager_mood_flags", () ->
                    AttachmentType.builder(() -> 0)
                            .sync(ByteBufCodecs.VAR_INT)
                            .build());

    // Per-chunk seasonal ground plants (SeasonalPlantService): player-placed positions that are exempt,
    // and dormant naturally generated plants waiting for their season. Serialized when non-empty.
    public static final Supplier<AttachmentType<com.terrainlab.season.plants.SeasonalPlantChunkData>> SEASONAL_PLANTS =
            ATTACHMENT_TYPES.register("seasonal_plants", () ->
                    AttachmentType.<com.terrainlab.season.plants.SeasonalPlantChunkData>builder(
                                    (Supplier<com.terrainlab.season.plants.SeasonalPlantChunkData>) com.terrainlab.season.plants.SeasonalPlantChunkData::new)
                            .serialize(com.terrainlab.season.plants.SeasonalPlantChunkData.MAP_CODEC, data -> !data.isEmpty())
                            .build());

    // The lead item that leashed this entity (only when it carries components, e.g. enchantments or a name),
    // so the lead dropped when the leash breaks is the same lead. See EnchantedLeadHandler. Serialized.
    public static final Supplier<AttachmentType<net.minecraft.world.item.ItemStack>> LEASH_LEAD_ITEM =
            ATTACHMENT_TYPES.register("leash_lead_item", () ->
                    AttachmentType.builder(() -> net.minecraft.world.item.ItemStack.EMPTY)
                            .serialize(net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.fieldOf("lead"), stack -> !stack.isEmpty())
                            .build());

    // Villager health and chest meals (VillagerHealthService, VillagerChestMealService). All serialized.
    // Remaining heal-over-time from chest food.
    public static final Supplier<AttachmentType<com.terrainlab.village.VillagerPendingHeal>> VILLAGER_PENDING_HEAL =
            ATTACHMENT_TYPES.register("villager_pending_heal", () ->
                    AttachmentType.builder(() -> com.terrainlab.village.VillagerPendingHeal.NONE)
                            .serialize(com.terrainlab.village.VillagerPendingHeal.MAP_CODEC, h -> !h.isEmpty())
                            .build());

    // Game time of the last 1 HP natural heal (or of the last read at full health); -1 = not started.
    public static final Supplier<AttachmentType<Long>> VILLAGER_LAST_NATURAL_HEAL_TICK =
            ATTACHMENT_TYPES.register("villager_last_natural_heal_tick", () ->
                    AttachmentType.builder(() -> -1L)
                            .serialize(Codec.LONG.fieldOf("villager_last_natural_heal_tick"), t -> t >= 0L)
                            .build());

    // Timed temperature offset from warming/cooling chest food, added to the villager's combined temperature.
    public static final Supplier<AttachmentType<com.terrainlab.village.VillagerFoodTempOffset>> VILLAGER_FOOD_TEMP_OFFSET =
            ATTACHMENT_TYPES.register("villager_food_temp_offset", () ->
                    AttachmentType.builder(() -> com.terrainlab.village.VillagerFoodTempOffset.NONE)
                            .serialize(com.terrainlab.village.VillagerFoodTempOffset.MAP_CODEC, o -> o.offset() != 0.0F)
                            .build());

    // Game time of the villager's last meal from the village chest (meal cooldown); -1 = never.
    public static final Supplier<AttachmentType<Long>> VILLAGER_LAST_CHEST_MEAL_TICK =
            ATTACHMENT_TYPES.register("villager_last_chest_meal_tick", () ->
                    AttachmentType.builder(() -> -1L)
                            .serialize(Codec.LONG.fieldOf("villager_last_chest_meal_tick"), t -> t >= 0L)
                            .build());

    // Game time the villager's eating animation ends; the held food is cleared after it (also after a
    // reload mid-meal). 0 = not eating.
    public static final Supplier<AttachmentType<Long>> VILLAGER_EATING_UNTIL =
            ATTACHMENT_TYPES.register("villager_eating_until", () ->
                    AttachmentType.builder(() -> 0L)
                            .serialize(Codec.LONG.fieldOf("villager_eating_until"), t -> t > 0L)
                            .build());

    // Profession work behaviour cooldowns (village.work): last chest delivery, slaughter, breeding per species. Serialized.
    public static final Supplier<AttachmentType<com.terrainlab.village.VillagerWorkState>> VILLAGER_WORK_STATE =
            ATTACHMENT_TYPES.register("villager_work_state", () ->
                    AttachmentType.builder(() -> com.terrainlab.village.VillagerWorkState.NONE)
                            .serialize(com.terrainlab.village.VillagerWorkState.MAP_CODEC, s -> !s.isEmpty())
                            .build());

    // Set on a wild caribou a butcher put in love, so it may breed untamed (AbstractHorseVillageLoveMixin). Transient.
    public static final Supplier<AttachmentType<Boolean>> CARIBOU_VILLAGE_LOVE =
            ATTACHMENT_TYPES.register("caribou_village_love", () ->
                    AttachmentType.builder(() -> false).build());

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
