package com.terrainlab;

import com.mojang.logging.LogUtils;
import com.terrainlab.event.*;
import com.terrainlab.registry.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.terrainlab.data.CodexSavedData;
import com.terrainlab.entity.GhostFishEntity;
import com.terrainlab.entity.GraniteGolemEntity;
import com.terrainlab.entity.FrostbittenEntity;
import com.terrainlab.entity.CaribouEntity;
import com.terrainlab.entity.OwlEntity;
import com.terrainlab.entity.VultureEntity;
import com.terrainlab.entity.GooseEntity;
import com.terrainlab.entity.GrizzlyBearEntity;
import com.terrainlab.entity.AbstractTrailTraderEntity;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.GameData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.slf4j.Logger;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Mod entry point: registers deferred registries and game-bus handlers, and dispatches per-tick server work. */
@Mod(TerrainLab.MODID)
public class TerrainLab {

    public static final String MODID = "terrainlab";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Enables Trail Trader spawn diagnostics (per-second biome sampling and ~10s summaries).
    private static final boolean DEBUG_TRAIL_TRADER_SPAWNS = false;

    // Debug: per-biome player exposure samples keyed by biome id, drained every ~10s.
    private static final Map<String, LongAdder> BIOME_EXPOSURE_SAMPLES = new ConcurrentHashMap<>();

    /** Debug helper: sample count for a biome, or 0 if it had none this window. */
    private static long sampleCount(Map<String, Long> snapshot, String biomeKey) {
        return snapshot.getOrDefault(biomeKey, 0L);
    }

    public TerrainLab(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        NeoForge.EVENT_BUS.register(ModSpawnHandler.class);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        NeoForge.EVENT_BUS.register(ModEndermanHandler.class);
        NeoForge.EVENT_BUS.register(ModLeashHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.MountHitchHandler.class);
        NeoForge.EVENT_BUS.register(ModSpawnRuleHandler.class);
        // Wood shield combat: auto-block, magic ward, fire/blast immunity, knockback scaling.
        NeoForge.EVENT_BUS.register(com.terrainlab.event.ShieldCombatEvents.class);
        // Tier-4 villages sing at the dusk gathering.
        NeoForge.EVENT_BUS.register(com.terrainlab.village.VillageSongService.class);
        // Populates the heat-source index read by HeatContributionService. The older scan-based
        // ModHeatSourceHandler is intentionally not registered.
        NeoForge.EVENT_BUS.register(HeatSourceIndexHandler.class);
        NeoForge.EVENT_BUS.register(ModGraniteGolemHandler.class);
        NeoForge.EVENT_BUS.register(SpyglassMapFillHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.AtlasAcquisitionHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.RedstonePortalHandler.class);
        ModNetwork.register(modEventBus);
        ModItems.register(modEventBus);
        com.terrainlab.registry.ModCreativeTabContents.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModSounds.register(modEventBus);
        ModStructures.register(modEventBus);
        ModAttachments.register(modEventBus);
        com.terrainlab.registry.ModParticles.register(modEventBus);
        com.terrainlab.registry.ModMapDecorations.register(modEventBus);
        com.terrainlab.advancement.ModAdvancements.register(modEventBus);
        ModMobEffects.register(modEventBus);
        ModPotions.register(modEventBus);
        ModDataComponents.register(modEventBus);
        com.terrainlab.registry.ModEnchantmentEffects.register(modEventBus);
        com.terrainlab.registry.ModBlockPredicateTypes.register(modEventBus);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.RecallDeathDropHandler.class);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(ModStructureHandler.class);
        NeoForge.EVENT_BUS.register(ModOceanMonumentRoomHandler.class);
        NeoForge.EVENT_BUS.register(ModAuroraHandler.class);
        modEventBus.addListener(this::onEntityAttributeCreation);
        modEventBus.addListener(this::onSpawnPlacementRegister);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.VillageInfoInteractionHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.ModSeasonSyncHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.ModSkyExposureHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.ModFoodEffectsHandler.class);
        ModRecipeSerializers.register(modEventBus);
        ModRecipeTypes.register(modEventBus);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.CelestialEventHandler.class);
        // Luck conduit XP doubling plus planting/taming/breeding XP rewards.
        NeoForge.EVENT_BUS.register(com.terrainlab.event.CelestialLuckXpHandler.class);
        // Animals at the exact same position cannot breed.
        NeoForge.EVENT_BUS.register(com.terrainlab.event.BreedingSameLocationHandler.class);

        // Recipe book: obtaining any ingredient unlocks the recipe (login scan here;
        // live unlocks come from InventoryChangeRecipeUnlockMixin).
        NeoForge.EVENT_BUS.register(com.terrainlab.event.RecipeUnlockHandler.class);

        // Villager behaviour, daily village tick and the villageinfo debug command.
        NeoForge.EVENT_BUS.register(com.terrainlab.event.VillagerBehaviorHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.VillageDailyTickHandler.class);
        // Villager healing rules (LivingHealEvent) and chest-meal eating animations.
        NeoForge.EVENT_BUS.register(com.terrainlab.village.VillagerHealthService.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.village.VillagerChestMealService.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.VillageEconomyHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.PlayerMilestoneHandler.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.event.StructureStatsCommand.class);

        // Custom trade tables replacing every vanilla profession's trade pool.
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.FarmerTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.LeatherworkerTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.ShepherdTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.ButcherTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.FishermanTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.MasonTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.CartographerTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.ClericTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.LibrarianTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.ToolsmithTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.ArmorerTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.FletcherTrades.class);
        NeoForge.EVENT_BUS.register(com.terrainlab.trade.WeaponsmithTrades.class);
        // WanderingTraderTrades is a static data holder with no @SubscribeEvent methods,
        // so it must not be registered here (register() would throw).
        com.terrainlab.registry.ModLootModifiers.register(modEventBus);

        // Client-only registrations; guarded so client classes never load on a dedicated server.
        if (FMLEnvironment.getDist().isClient()) {
            com.terrainlab.client.TerrainLabItemModelProperties.register(modEventBus);
            NeoForge.EVENT_BUS.register(com.terrainlab.client.renderer.AuroraSkyRenderer.class);
            // Celestial Death respawn UI: death-screen button and spectator location picking.
            NeoForge.EVENT_BUS.register(com.terrainlab.client.CelestialDeathScreenHandler.class);
            NeoForge.EVENT_BUS.register(com.terrainlab.client.CelestialDeathPickingClientHandler.class);
        }
    }

    public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GRANITE_GOLEM.get(),
                GraniteGolemEntity.createAttributes().build());
        event.put(ModEntities.GHOST_FISH.get(),
                GhostFishEntity.createAttributes().build());

        // These reuse their vanilla parent's attributes; no stat tuning yet.
        event.put(ModEntities.FROSTBITTEN.get(),
                Zombie.createAttributes().build());
        event.put(ModEntities.CARIBOU.get(),
                Llama.createAttributes().build());
        event.put(ModEntities.GRIZZLY_BEAR.get(),
                PolarBear.createAttributes().build());
        event.put(ModEntities.ELEPHANT.get(),
                com.terrainlab.entity.ElephantEntity.createAttributes().build());
        event.put(ModEntities.OWL.get(),
                Parrot.createAttributes().add(Attributes.FLYING_SPEED, 0.6).build());
        event.put(ModEntities.GOOSE.get(),
                Parrot.createAttributes().add(Attributes.FLYING_SPEED, 0.6).build());
        event.put(ModEntities.VULTURE.get(),
                Parrot.createAttributes().add(Attributes.FLYING_SPEED, 0.7).build());

        // Trail Traders: WanderingTrader has no static createAttributes(), so vanilla trader
        // defaults are built directly.
        event.put(ModEntities.RANGER.get(), createTrailTraderAttributes().build());
        event.put(ModEntities.ARCHEOLOGIST.get(), createTrailTraderAttributes().build());
        event.put(ModEntities.MARINER.get(), createTrailTraderAttributes().build());
        event.put(ModEntities.PRIEST.get(), createTrailTraderAttributes().build());

        // Pillager raider variants share vanilla Pillager attributes.
        event.put(ModEntities.PIRATE_RAIDER.get(), Pillager.createAttributes().build());
        event.put(ModEntities.OUTLAW_RAIDER.get(), Pillager.createAttributes().build());
        event.put(ModEntities.TRAPPER_RAIDER.get(), Pillager.createAttributes().build());
    }

    private static AttributeSupplier.Builder createTrailTraderAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    // Mod-bus listener. Only sets placement predicates; which biomes spawn each mob
    // is defined in biome data.
    public void onSpawnPlacementRegister(RegisterSpawnPlacementsEvent event) {
        event.register(
                ModEntities.GHOST_FISH.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GhostFishEntity::checkGhostFishSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.GOOSE.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GooseEntity::checkGooseSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);
        event.register(
                ModEntities.VULTURE.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                VultureEntity::checkVultureSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);
        // com.terrainlab.TerrainLab.LOGGER.info("[SPAWN_PLACEMENT_REGISTER] registered VULTURE");

        event.register(
                ModEntities.FROSTBITTEN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FrostbittenEntity::checkFrostbittenSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.CARIBOU.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CaribouEntity::checkCaribouSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.OWL.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING,
                OwlEntity::checkOwlSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.GRIZZLY_BEAR.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GrizzlyBearEntity::checkGrizzlyBearSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        // Pigs may also spawn on podzol (taiga floors), with vanilla's light rule; OR keeps
        // vanilla's grass rule. Extending a vanilla entity's rule may not set a placement type or
        // heightmap (only REPLACE can), so this overload keeps vanilla's.
        event.register(
                net.minecraft.world.entity.EntityType.PIG,
                (type, level, reason, pos, random) ->
                        level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.PODZOL)
                                && (net.minecraft.world.entity.EntitySpawnReason.ignoresLightRequirements(reason)
                                || level.getRawBrightness(pos, 0) > 8),
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.ELEPHANT.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                com.terrainlab.entity.ElephantEntity::checkElephantSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        // Trail Traders share one spawn rule; their biomes come from the
        // terrainlab:*_spawns biome modifiers.
        event.register(
                ModEntities.RANGER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbstractTrailTraderEntity::checkTrailTraderSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.ARCHEOLOGIST.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbstractTrailTraderEntity::checkTrailTraderSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.MARINER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbstractTrailTraderEntity::checkTrailTraderSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.PRIEST.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbstractTrailTraderEntity::checkTrailTraderSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        // Raider variants spawn via their structures' spawn_overrides, which still run
        // this placement check.
        event.register(
                ModEntities.PIRATE_RAIDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.OUTLAW_RAIDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);

        event.register(
                ModEntities.TRAPPER_RAIDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.OR);
    }

    @SubscribeEvent
    public void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // Debug: sample each online player's biome once per second.
        if (DEBUG_TRAIL_TRADER_SPAWNS && event.getServer().getTickCount() % 20 == 0) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (net.minecraft.world.entity.player.Player player : level.players()) {
                    Holder<net.minecraft.world.level.biome.Biome> biomeHolder =
                            level.getBiome(player.blockPosition());
                    String biomeName = biomeHolder.unwrapKey()
                            .map(key -> key.identifier().toString())
                            .orElse("unknown");
                    BIOME_EXPOSURE_SAMPLES.computeIfAbsent(biomeName, b -> new LongAdder()).increment();
                }
            }
        }

        // Debug: every ~10s, log per-type Trail Trader ground-check attempts and passes,
        // plus player exposure to each target biome.
        if (DEBUG_TRAIL_TRADER_SPAWNS && event.getServer().getTickCount() % 200 == 0) {
            var attempts = AbstractTrailTraderEntity.drainGroundCheckAttempts();
            LOGGER.info(
                    "[TrailTraderSpawnDebug] ground-check attempts in the last ~10s: ranger={}, archeologist={}, mariner={}, priest={}",
                    attempts.getOrDefault(ModEntities.RANGER.get(), 0L),
                    attempts.getOrDefault(ModEntities.ARCHEOLOGIST.get(), 0L),
                    attempts.getOrDefault(ModEntities.MARINER.get(), 0L),
                    attempts.getOrDefault(ModEntities.PRIEST.get(), 0L));

            var passes = AbstractTrailTraderEntity.drainGroundCheckPasses();
            LOGGER.info(
                    "[TrailTraderSpawnDebug] ground-check PASSES (of the attempts above) in the last ~10s: ranger={}, archeologist={}, mariner={}, priest={}",
                    passes.getOrDefault(ModEntities.RANGER.get(), 0L),
                    passes.getOrDefault(ModEntities.ARCHEOLOGIST.get(), 0L),
                    passes.getOrDefault(ModEntities.MARINER.get(), 0L),
                    passes.getOrDefault(ModEntities.PRIEST.get(), 0L));

            Map<String, Long> exposureSnapshot = new java.util.HashMap<>();
            BIOME_EXPOSURE_SAMPLES.forEach((biome, adder) -> exposureSnapshot.put(biome, adder.sumThenReset()));
            LOGGER.info(
                    "[TrailTraderSpawnDebug] biome exposure (player samples, ~1/sec) in the last ~10s: "
                            + "badlands={}, wooded_badlands={}, old_growth_pine_taiga={}, old_growth_spruce_taiga={}, "
                            + "old_growth_birch_forest={}, taiga={}, swamp={}, terrainlab_marsh={}, beach={}, stony_shore={}",
                    sampleCount(exposureSnapshot, "minecraft:badlands"),
                    sampleCount(exposureSnapshot, "minecraft:wooded_badlands"),
                    sampleCount(exposureSnapshot, "minecraft:old_growth_pine_taiga"),
                    sampleCount(exposureSnapshot, "minecraft:old_growth_spruce_taiga"),
                    sampleCount(exposureSnapshot, "minecraft:old_growth_birch_forest"),
                    sampleCount(exposureSnapshot, "minecraft:taiga"),
                    sampleCount(exposureSnapshot, "minecraft:swamp"),
                    sampleCount(exposureSnapshot, "terrainlab:marsh"),
                    sampleCount(exposureSnapshot, "minecraft:beach"),
                    sampleCount(exposureSnapshot, "minecraft:stony_shore"));
        }

        // Every ~5s, reattach cargo donkeys that have come loose from their trader's leash.
        if (event.getServer().getTickCount() % 100 == 0) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                AbstractTrailTraderEntity.tickCargoDonkeyLeashWatchdog(level);
            }
        }

        // Extra random ticks for Endervine heads near End players.
        if (event.getServer().getTickCount() % ENDERVINE_TICK_INTERVAL == 0) {
            ServerLevel end = event.getServer().getLevel(net.minecraft.world.level.Level.END);
            if (end != null) {
                for (net.minecraft.world.entity.player.Player player : end.players()) {
                    if (!player.isSpectator()) {
                        tickEndervineNear(end, player.blockPosition());
                    }
                }
            }
        }

        // Player temperature, energy, hunger and healing tick every tick (no staggering).
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (net.minecraft.world.entity.player.Player player : level.players()) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    com.terrainlab.temperature.PlayerInternalTemperature.tick(serverPlayer);
                    com.terrainlab.energy.PlayerEnergySystem.tick(serverPlayer);
                    com.terrainlab.energy.PlayerEnergyEffects.tick(serverPlayer);
                    com.terrainlab.energy.PlayerHungerSystem.tick(serverPlayer);
                    com.terrainlab.food.PendingHealSystem.tick(serverPlayer);
                }
            }
        }

        // Villager and village ticking live in VillagerBehaviorHandler and VillageDailyTickHandler.
    }

    private static final int ENDERVINE_TICK_INTERVAL = 20; // ticks
    private static final int ENDERVINE_TICK_RADIUS = 8;    // blocks; heads only grow with a player this close

    /**
     * Random-ticks every Endervine head within {@link #ENDERVINE_TICK_RADIUS} of a position,
     * reading only loaded chunk sections whose palette can contain one.
     */
    private static void tickEndervineNear(ServerLevel level, BlockPos centre) {
        BlockState head = ModBlocks.ENDERVINE_HEAD.get().defaultBlockState();
        int r = ENDERVINE_TICK_RADIUS;
        int minSection = net.minecraft.core.SectionPos.blockToSectionCoord(centre.getY() - r);
        int maxSection = net.minecraft.core.SectionPos.blockToSectionCoord(centre.getY() + r);
        int minChunkX = (centre.getX() - r) >> 4, maxChunkX = (centre.getX() + r) >> 4;
        int minChunkZ = (centre.getZ() - r) >> 4, maxChunkZ = (centre.getZ() + r) >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (int sy = minSection; sy <= maxSection; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSectionsCount()) continue;
                    net.minecraft.world.level.chunk.LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(head.getBlock()))) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!state.is(head.getBlock())) continue;
                                BlockPos pos = new BlockPos((cx << 4) + x, (sy << 4) + y, (cz << 4) + z);
                                if (pos.closerThan(centre, r)) {
                                    state.randomTick(level, pos, level.getRandom());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Makes the mod's FletchingTableBlock the fletcher job-site POI in place of vanilla's
     * fletching table, by editing the live block-state to POI map.
     */
    private void registerFletchingTablePoi(ServerLevel overworld) {
        Holder<PoiType> fletcherPoi = overworld.registryAccess()
                .lookupOrThrow(Registries.POINT_OF_INTEREST_TYPE)
                .getOrThrow(PoiTypes.FLETCHER);

        // Vanilla's fletching table no longer counts as a fletcher job site.
        for (BlockState state : Blocks.FLETCHING_TABLE.getStateDefinition().getPossibleStates()) {
            GameData.getBlockStatePointOfInterestTypeMap().remove(state);
        }

        for (BlockState state : ModBlocks.FLETCHING_TABLE.get().getStateDefinition().getPossibleStates()) {
            GameData.getBlockStatePointOfInterestTypeMap().put(state, fletcherPoi);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServerLevel overworld = event.getServer().overworld();

        registerFletchingTablePoi(overworld);

        long seed = overworld.getSeed();
        CodexSavedData data = CodexSavedData.get(overworld);
        // Idempotent: generates on a new world and maps any newly added enchantments on an existing one.
        data.generateMappings(seed, overworld.registryAccess());
        // LOGGER.info("CodexSavedData loaded, mappings generated: {}",
                // data.isMappingGenerated());

        int nullCount = 0;
        int nonNullCount = 0;
        for (int a = 0; a < 6; a++) {
            for (int b = a + 1; b < 7; b++) {
                for (int c = b + 1; c < 8; c++) {
                    net.minecraft.resources.Identifier result =
                            data.getEnchantment(new int[]{a, b, c});
                    if (result == null) nullCount++;
                    else nonNullCount++;
                }
            }
        }
        // LOGGER.info("Mapping check: {} enchantments, {} failed combinations (expected ~40 and ~16)",
                // nonNullCount, nullCount);
    }
}
