package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.season.SolarTime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Daytime removal of outdoor hostiles that have not seen a player for 30 seconds. */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class DaylightDespawnHandler {

    private DaylightDespawnHandler() {
    }

    public static final TagKey<EntityType<?>> DAYLIGHT_DESPAWN = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "daylight_despawn"));

    private static final Identifier WITCH = Identifier.withDefaultNamespace("witch");

    /** 07:00 */
    private static final long START_AFTER_SUNRISE_TICKS = 1000L;

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int UNSEEN_LIMIT_TICKS = 600; // 30 s
    private static final double SIGHT_RANGE = 64.0;

    private static final Map<Mob, Integer> UNSEEN = new WeakHashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;
        long sunTime = SolarTime.sunTimeOfDay(level);
        if (sunTime < START_AFTER_SUNRISE_TICKS || sunTime >= 12000L) {
            UNSEEN.clear();
            return;
        }

        List<Mob> remove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || !mob.isAlive() || !mob.getType().builtInRegistryHolder().is(DAYLIGHT_DESPAWN)) continue;
            if (!eligible(mob) || !isOutdoors(level, mob) || seesPlayer(level, mob)) {
                UNSEEN.remove(mob);
                continue;
            }
            int unseen = UNSEEN.merge(mob, CHECK_INTERVAL_TICKS, Integer::sum);
            if (unseen >= UNSEEN_LIMIT_TICKS) remove.add(mob);
        }
        for (Mob mob : remove) {
            UNSEEN.remove(mob);
            mob.discard();
        }
    }

    private static boolean eligible(Mob mob) {
        if (mob.hasCustomName()) return false;
        if (mob instanceof Raider raider && raider.getCurrentRaid() != null) return false;
        return !(mob.isPersistenceRequired() && WITCH.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())));
    }

    /** Open sky, ignoring leaves. */
    private static boolean isOutdoors(ServerLevel level, Mob mob) {
        BlockPos eyes = BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ());
        return eyes.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, eyes.getX(), eyes.getZ());
    }

    private static boolean seesPlayer(ServerLevel level, Mob mob) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(mob) > SIGHT_RANGE * SIGHT_RANGE) continue;
            if (mob.hasLineOfSight(player)) return true;
        }
        return false;
    }
}
