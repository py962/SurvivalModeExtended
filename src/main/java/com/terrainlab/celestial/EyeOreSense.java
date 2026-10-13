package com.terrainlab.celestial;

import com.terrainlab.mixin.BlockDisplayInvoker;
import com.terrainlab.mixin.DisplayInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.EntityTypes;

/**
 * Eye conduit ore vision. Around each player in range of an active Eye conduit, ores within
 * {@link #SEARCH_RADIUS} whose line of sight crosses at most {@link #WALL_LIMIT} solid blocks
 * get a glowing BlockDisplay copy; vanilla's outline pass draws it through walls. Outlines are
 * visible to all players.
 *
 * <p>Markers expire {@link #MARKER_LIFETIME_TICKS} after their last re-confirmation and are
 * also removed when the ore changes or no player is nearby. Tagged markers loaded from disk are
 * rejected so none linger after a chunk unload or server stop.
 *
 * <p>Client-side entity-culling mods may hide these outlines; whitelisting
 * {@code minecraft:block_display} there fixes that.
 */
public final class EyeOreSense {

    public static final int SEARCH_RADIUS = 12;
    public static final int WALL_LIMIT = 8;
    private static final int MARKER_LIFETIME_TICKS = 20;
    private static final int CLEANUP_INTERVAL = 10;
    /** Per-player cap per scan so ore-dense spots cannot spawn hundreds of entities; nearest ores win. */
    private static final int MAX_MARKERS_PER_PLAYER = 64;
    public static final String MARKER_TAG = "terrainlab_eye_ore_marker";

    /** Ore tag -> glow colour (0xRRGGBB). Checked in order. */
    private static final Map<TagKey<Block>, Integer> ORE_COLOURS = new LinkedHashMap<>();

    static {
        ORE_COLOURS.put(BlockItemTags.DIAMOND_ORES.block(), 0x5DECF5);
        ORE_COLOURS.put(BlockItemTags.EMERALD_ORES.block(), 0x17DD62);
        ORE_COLOURS.put(BlockTags.GOLD_ORES, 0xFCEE4B);
        ORE_COLOURS.put(BlockTags.IRON_ORES, 0xD8AF93);
        ORE_COLOURS.put(BlockItemTags.LAPIS_ORES.block(), 0x345EC3);
        ORE_COLOURS.put(BlockItemTags.REDSTONE_ORES.block(), 0xFF2020);
        ORE_COLOURS.put(BlockTags.COPPER_ORES, 0xE0734D);
        ORE_COLOURS.put(BlockItemTags.COAL_ORES.block(), 0x8A8A8A);
    }

    // Ores with no block tag, matched by block.
    private static final Map<Block, Integer> ORE_BLOCK_COLOURS = new LinkedHashMap<>();
    static {
        ORE_BLOCK_COLOURS.put(Blocks.NETHER_QUARTZ_ORE, 0xEDE6DE);
        ORE_BLOCK_COLOURS.put(Blocks.ANCIENT_DEBRIS, 0x8B5A4A);
    }

    private record Marker(UUID entityId, BlockState state, long expiresAt) {
    }

    private static final Map<ResourceKey<Level>, Map<BlockPos, Marker>> MARKERS = new HashMap<>();

    private EyeOreSense() {
    }

    /** Scans around a player in range of an active Eye conduit. Called every 10 ticks from
     * CelestialEventHandler#onPlayerTick. */
    public static void scanForPlayer(ServerLevel level, ServerPlayer player) {
        Map<BlockPos, Marker> levelMarkers = MARKERS.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long now = level.getGameTime();
        {
            Vec3 eye = player.getEyePosition();
            BlockPos centre = BlockPos.containing(eye);
            int found = 0;
            int blockedByWalls = 0;
            int spawned = 0;
            int refreshed = 0;

            // Nearest first, so the per-player cap keeps the closest ores.
            java.util.List<BlockPos> candidates = new java.util.ArrayList<>();
            for (BlockPos check : BlockPos.betweenClosed(
                    centre.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                    centre.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
                if (check.distSqr(centre) > (double) SEARCH_RADIUS * SEARCH_RADIUS) continue;
                if (colourFor(level.getBlockState(check)) == null) continue;
                candidates.add(check.immutable());
            }
            candidates.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(centre)));

            for (BlockPos orePos : candidates) {
                found++;
                if (spawned + refreshed >= MAX_MARKERS_PER_PLAYER) break;
                int walls = countSolidBlocksBetween(level, eye, Vec3.atCenterOf(orePos), orePos);
                if (walls > WALL_LIMIT) {
                    blockedByWalls++;
                    continue;
                }
                BlockState state = level.getBlockState(orePos);
                Marker existing = levelMarkers.get(orePos);
                if (existing != null && existing.state() == state && level.getEntity(existing.entityId()) != null) {
                    levelMarkers.put(orePos, new Marker(existing.entityId(), state, now + MARKER_LIFETIME_TICKS));
                    refreshed++;
                    continue;
                }
                if (existing != null) {
                    discard(level, existing);
                }
                UUID id = spawnMarker(level, orePos, state);
                if (id != null) {
                    levelMarkers.put(orePos, new Marker(id, state, now + MARKER_LIFETIME_TICKS));
                    spawned++;
                }
            }

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("  EYE: {} at {} - {} ore(s) within {}, {} hidden by >{} walls, {} marker(s) spawned, {} refreshed (live markers in {}: {})",
                        player.getName().getString(), centre, found, SEARCH_RADIUS, blockedByWalls, WALL_LIMIT,
                        spawned, refreshed, level.dimension().identifier(), levelMarkers.size());
            }
        }
    }

    /** Every CLEANUP_INTERVAL ticks: drops expired markers, markers whose ore changed, and markers
     * no longer within SEARCH_RADIUS + 1 of any player. */
    public static void cleanup(ServerLevel level) {
        if (level.getGameTime() % CLEANUP_INTERVAL != 0) return;
        Map<BlockPos, Marker> levelMarkers = MARKERS.get(level.dimension());
        if (levelMarkers == null || levelMarkers.isEmpty()) return;

        long now = level.getGameTime();
        int expired = 0;
        int oreGone = 0;
        int outOfRange = 0;
        double keepDistSq = (SEARCH_RADIUS + 1.0) * (SEARCH_RADIUS + 1.0);
        Iterator<Map.Entry<BlockPos, Marker>> it = levelMarkers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Marker> entry = it.next();
            Marker marker = entry.getValue();
            boolean isExpired = now >= marker.expiresAt();
            boolean changed = level.isLoaded(entry.getKey()) && level.getBlockState(entry.getKey()) != marker.state();
            Vec3 oreCentre = Vec3.atCenterOf(entry.getKey());
            boolean nearSomePlayer = false;
            for (ServerPlayer player : level.players()) {
                if (player.getEyePosition().distanceToSqr(oreCentre) <= keepDistSq) {
                    nearSomePlayer = true;
                    break;
                }
            }
            if (isExpired || changed || !nearSomePlayer) {
                discard(level, marker);
                it.remove();
                if (isExpired) expired++; else if (changed) oreGone++; else outOfRange++;
            }
        }
        if (CelestialDebug.VERBOSE && (expired > 0 || oreGone > 0 || outOfRange > 0)) {
            CelestialDebug.log("EYE cleanup in {}: removed {} expired + {} mined/changed + {} out-of-range marker(s), {} left",
                    level.dimension().identifier(), expired, oreGone, outOfRange, levelMarkers.size());
        }
    }

    /** Rejects tagged markers loaded back from disk. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.loadedFromDisk() && entity.entityTags().contains(MARKER_TAG)) {
            event.setCanceled(true);
            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("EYE: dropped stale ore marker loaded from disk at {}", entity.blockPosition());
            }
        }
    }

    private static UUID spawnMarker(ServerLevel level, BlockPos pos, BlockState state) {
        Integer colour = colourFor(state);
        Display.BlockDisplay display = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (display == null || colour == null) {
            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("  EYE: could not create marker at {} (display={}, colour={})", pos, display, colour);
            }
            return null;
        }
        // BlockDisplay renders from its origin towards +x/+y/+z, so the block corner aligns it with the ore.
        display.snapTo(pos.getX(), pos.getY(), pos.getZ());
        ((BlockDisplayInvoker) display).terrainlab$setBlockState(state);
        ((DisplayInvoker) display).terrainlab$setGlowColorOverride(colour);
        display.setGlowingTag(true);
        display.addTag(MARKER_TAG);
        if (!level.addFreshEntity(display)) {
            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("  EYE: addFreshEntity refused marker at {}", pos);
            }
            return null;
        }
        return display.getUUID();
    }

    private static void discard(ServerLevel level, Marker marker) {
        Entity entity = level.getEntity(marker.entityId());
        if (entity != null) {
            entity.discard();
        }
    }

    private static Integer colourFor(BlockState state) {
        for (Map.Entry<TagKey<Block>, Integer> entry : ORE_COLOURS.entrySet()) {
            if (state.is(entry.getKey())) return entry.getValue();
        }
        return ORE_BLOCK_COLOURS.get(state.getBlock());
    }

    /** Approximate line march (~0.5-block sampling) counting distinct solid blocks between two points,
     * excluding the target block. Returns early once over WALL_LIMIT. */
    private static int countSolidBlocksBetween(ServerLevel level, Vec3 from, Vec3 to, BlockPos target) {
        double dist = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(dist * 2.0));
        BlockPos lastPos = null;
        int solidCount = 0;
        for (int i = 1; i < steps; i++) {
            Vec3 point = from.lerp(to, (double) i / steps);
            BlockPos sample = BlockPos.containing(point);
            if (sample.equals(lastPos) || sample.equals(target)) {
                continue;
            }
            lastPos = sample;
            if (!level.getBlockState(sample).getCollisionShape(level, sample).isEmpty()) {
                solidCount++;
                if (solidCount > WALL_LIMIT) {
                    return solidCount;
                }
            }
        }
        return solidCount;
    }
}
