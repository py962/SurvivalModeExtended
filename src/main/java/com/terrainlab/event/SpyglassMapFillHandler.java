package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * While a player scopes with a spyglass holding a filled map in the off hand, computes the
 * loaded chunks inside a narrow view cone (up to {@link #MAX_DIST} blocks) for
 * MapItemMixin to paint onto that map. The cone is recomputed every {@link #SCAN_INTERVAL_TICKS}
 * ticks, and each chunk is handed to the mixin once per scoping session.
 */
public class SpyglassMapFillHandler {

    private static final double MAX_DIST = 512.0; // blocks
    private static final double HALF_FOV_DEG = 9.0;
    private static final double COS_HALF_FOV = Math.cos(Math.toRadians(HALF_FOV_DEG));

    private static final int SCAN_INTERVAL_TICKS = 10;

    /** Chunks flagged but not yet painted in the current session. */
    private static final Map<Player, Set<ChunkPos>> FLAGGED_CACHE = new ConcurrentHashMap<>();

    /** Chunks already handed out in the current session, and the map that session paints. */
    private static final Map<Player, ScopeSession> SESSIONS = new ConcurrentHashMap<>();

    private static final class ScopeSession {
        final MapId mapId;
        final Set<ChunkPos> painted = new HashSet<>();

        ScopeSession(MapId mapId) {
            this.mapId = mapId;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();

        if (player.level().isClientSide()) return;

        boolean usingSpyglass = player.isUsingItem() && player.getUseItem().is(net.minecraft.world.item.Items.SPYGLASS);
        boolean hasMapInOffhand = player.getOffhandItem().is(net.minecraft.world.item.Items.FILLED_MAP);

        if (!usingSpyglass || !hasMapInOffhand) {
            FLAGGED_CACHE.remove(player);
            SESSIONS.remove(player);
            return;
        }

        MapId mapId = player.getOffhandItem().get(DataComponents.MAP_ID);
        ScopeSession session = SESSIONS.get(player);
        if (session == null || !Objects.equals(session.mapId, mapId)) {
            session = new ScopeSession(mapId);
            SESSIONS.put(player, session);
            FLAGGED_CACHE.remove(player);
        } else if (player.tickCount % SCAN_INTERVAL_TICKS != 0) {
            return;
        }

        Set<ChunkPos> flagged = computeFlaggedChunks(player);
        // TerrainLab.LOGGER.info("[SPYGLASS_MAP] {} chunks flagged for {}", flagged.size(), player.getUUID());
        boolean anyFlagged = !flagged.isEmpty();
        flagged.removeAll(session.painted);
        Set<ChunkPos> pending = FLAGGED_CACHE.computeIfAbsent(player, p -> new HashSet<>());
        pending.addAll(flagged);
        if (anyFlagged && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.terrainlab.advancement.ModAdvancements.award(serverPlayer, "spyglass_map");
        }
    }

    public static Set<ChunkPos> getFlaggedChunks(Player player) {
        return FLAGGED_CACHE.getOrDefault(player, Set.of());
    }

    /**
     * Returns the flagged chunks not yet painted this session and marks them painted, so each chunk is
     * sampled once while the player keeps scoping with the same map.
     */
    public static Set<ChunkPos> takeUnpaintedChunks(Player player) {
        Set<ChunkPos> pending = FLAGGED_CACHE.remove(player);
        if (pending == null || pending.isEmpty()) {
            return Set.of();
        }
        ScopeSession session = SESSIONS.get(player);
        if (session != null) {
            session.painted.addAll(pending);
        }
        return pending;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        FLAGGED_CACHE.remove(event.getEntity());
        SESSIONS.remove(event.getEntity());
    }

    private static Set<ChunkPos> computeFlaggedChunks(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 flatLook = new Vec3(look.x, 0, look.z).normalize();
        int maxChunkRadius = (int) Math.ceil(MAX_DIST / 16.0);
        ChunkPos center = player.chunkPosition();

        Set<ChunkPos> result = new HashSet<>();
        for (int dx = -maxChunkRadius; dx <= maxChunkRadius; dx++) {
            for (int dz = -maxChunkRadius; dz <= maxChunkRadius; dz++) {
                int cx = center.x() + dx;
                int cz = center.z() + dz;
                if (!player.level().hasChunk(cx, cz)) continue;

                double wx = cx * 16 + 8;
                double wz = cz * 16 + 8;
                Vec3 toChunk = new Vec3(wx - eye.x, 0, wz - eye.z);
                if (toChunk.lengthSqr() > MAX_DIST * MAX_DIST) continue;

                Vec3 dir = toChunk.normalize();
                if (dir.dot(flatLook) < COS_HALF_FOV) continue;

                result.add(new ChunkPos(cx, cz));
            }
        }
        return result;
    }
}
