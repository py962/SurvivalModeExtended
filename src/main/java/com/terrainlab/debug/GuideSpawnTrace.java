package com.terrainlab.debug;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.AbstractTrailTraderEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Guide (Trail Trader) spawn trace, on with {@code -Dterrainlab.debug=true}. Every spawn-rule check
 * is counted by the stage it reached, and every guide that actually spawns is logged with its
 * position, biome and reason. Every 10 s, one line per guide type that had any checks:
 * <pre>[TerrainLab/guides] archeologist: 412 checks -> 1 passed | ground 230, biome 160, near destination 3,
 * bonded guide exists 0, spawn claim active 18, world record 0 | players in its biomes 7 s</pre>
 * Natural guide spawns mostly happen during chunk generation, so the checks come from new terrain
 * being generated, not from where the player stands.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class GuideSpawnTrace {

    public enum Stage {
        CHECK, GROUND, BIOME, NEAR_DESTINATION, BONDED_EXISTS, CLAIM_ACTIVE, WORLD_RECORD, PASS
    }

    private static final int SUMMARY_INTERVAL = 200;

    private static final Map<EntityType<?>, EnumMap<Stage, LongAdder>> COUNTS = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, LongAdder> PLAYER_SECONDS = new ConcurrentHashMap<>();

    private GuideSpawnTrace() {
    }

    /** Counts one spawn-rule outcome. Cheap no-op when debugging is off. */
    public static void record(EntityType<?> type, Stage stage) {
        if (!TerrainLabDebug.ENABLED) {
            return;
        }
        COUNTS.computeIfAbsent(type, t -> {
            EnumMap<Stage, LongAdder> map = new EnumMap<>(Stage.class);
            for (Stage s : Stage.values()) {
                map.put(s, new LongAdder());
            }
            return map;
        }).get(stage).increment();
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!TerrainLabDebug.ENABLED || !(event.getEntity() instanceof AbstractTrailTraderEntity guide)) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        String biome = event.getLevel().getBiome(pos).unwrapKey()
                .map(key -> key.identifier().toString()).orElse("?");
        TerrainLab.LOGGER.info("[TerrainLab/guides] SPAWNED {} at {} in {} (reason {}, thread {})",
                name(guide.getType()), pos.toShortString(), biome, event.getSpawnType(),
                Thread.currentThread().getName());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!TerrainLabDebug.ENABLED) {
            return;
        }
        int tick = event.getServer().getTickCount();

        // Once a second: which guide types could spawn where the players are standing.
        if (tick % 20 == 0) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    for (EntityType<?> type : AbstractTrailTraderEntity.guideTypes()) {
                        if (AbstractTrailTraderEntity.biomeListsType(level, player.blockPosition(), type)) {
                            PLAYER_SECONDS.computeIfAbsent(type, t -> new LongAdder()).increment();
                        }
                    }
                }
            }
        }

        if (tick % SUMMARY_INTERVAL != 0) {
            return;
        }
        for (EntityType<?> type : AbstractTrailTraderEntity.guideTypes()) {
            EnumMap<Stage, LongAdder> counts = COUNTS.get(type);
            long seconds = PLAYER_SECONDS.containsKey(type) ? PLAYER_SECONDS.get(type).sumThenReset() : 0;
            long checks = counts == null ? 0 : counts.get(Stage.CHECK).sumThenReset();
            if (checks == 0 && seconds == 0) {
                continue;
            }
            TerrainLab.LOGGER.info("[TerrainLab/guides] {}: {} checks -> {} passed | ground {}, biome {}, "
                            + "near destination {}, bonded guide exists {}, spawn claim active {}, world record {} "
                            + "| players in its biomes {} s",
                    name(type), checks, take(counts, Stage.PASS), take(counts, Stage.GROUND), take(counts, Stage.BIOME),
                    take(counts, Stage.NEAR_DESTINATION), take(counts, Stage.BONDED_EXISTS),
                    take(counts, Stage.CLAIM_ACTIVE), take(counts, Stage.WORLD_RECORD), seconds);
        }
    }

    private static long take(EnumMap<Stage, LongAdder> counts, Stage stage) {
        return counts == null ? 0 : counts.get(stage).sumThenReset();
    }

    private static String name(EntityType<?> type) {
        return type.toShortString();
    }
}
