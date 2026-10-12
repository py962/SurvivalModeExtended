package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.util.SafeSpotFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Makes non-player mobs riding any {@link AbstractBoat} (including houseboats) disembark once they
 * have been aboard for 5 seconds and solid ground is within reach. Boarding times are tracked in
 * memory only.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class MobBoatDismountHandler {

    private static final boolean DEBUG_LOGGING = false;

    private static final int DISMOUNT_DELAY_TICKS = 100; // 5 seconds
    private static final int GROUND_SEARCH_RADIUS = 3;

    // Game time and dimension in which each tracked mob boarded a boat; cleared on any dismount.
    private static final Map<UUID, Boarding> BOARD_TICK = new HashMap<>();

    private record Boarding(long boardedAt, ResourceKey<Level> dimension) {
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting()) {
            // Covers every dismount path, including the stopRiding call below.
            BOARD_TICK.remove(event.getEntityMounting().getUUID());
            return;
        }

        if (!(event.getEntityMounting() instanceof Mob mob)) return; // excludes Player
        if (!(event.getEntityBeingMounted() instanceof AbstractBoat)) return;
        if (mob.level().isClientSide()) return;

        BOARD_TICK.put(mob.getUUID(), new Boarding(mob.level().getGameTime(), mob.level().dimension()));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (BOARD_TICK.isEmpty()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        long now = level.getGameTime();

        // Iterate a copy: stopRiding re-enters onMount, which removes from BOARD_TICK.
        for (UUID id : BOARD_TICK.keySet().toArray(new UUID[0])) {
            Boarding boarding = BOARD_TICK.get(id);
            if (boarding == null) continue;
            if (!boarding.dimension().equals(level.dimension())) continue; // handled by its own level's tick
            if (now - boarding.boardedAt() < DISMOUNT_DELAY_TICKS) continue;

            Entity entity = level.getEntity(id);
            if (!(entity instanceof Mob mob)) continue; // not currently loaded; keep tracking
            if (!mob.isAlive()) {
                BOARD_TICK.remove(id);
                continue;
            }
            if (!(mob.getVehicle() instanceof AbstractBoat boat)) {
                BOARD_TICK.remove(id);
                continue;
            }

            BlockPos safe = SafeSpotFinder.findNearestSafeSpot(
                    level, boat.blockPosition(), GROUND_SEARCH_RADIUS);
            if (safe == null) continue; // no ground in reach yet; retry next tick

            if (DEBUG_LOGGING) {
                // TerrainLab.LOGGER.info("[MobBoatDismount] {} disembarking at {}", id, safe);
            }

            mob.stopRiding();
            mob.teleportTo(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
            BOARD_TICK.remove(id);
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (BOARD_TICK.isEmpty() || event.getLevel().isClientSide()) return;
        Entity.RemovalReason reason = event.getEntity().getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            BOARD_TICK.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BOARD_TICK.clear();
    }
}
