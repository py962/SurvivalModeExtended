package com.terrainlab.entity;

import com.terrainlab.TerrainLab;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Keeps Trail Trader guides clear of every guide map destination. A destination is
 * "near" when any of its pieces lies within {@link #AVOID_RADIUS} blocks horizontally
 * and 16 blocks vertically. Used by guide spawn rules, by {@link AvoidGoal}, and by
 * bonded-guide teleports. Only reads chunks that are already loaded; never loads or
 * generates one.
 */
public final class GuideStructureAvoidance {

    public static final int AVOID_RADIUS = 100; // blocks
    private static final int VERTICAL_MARGIN = 16; // blocks
    private static final int CHECK_INTERVAL = 40; // ticks

    /** Destinations of all four guides (Ranger, Archeologist, Mariner, Priest). */
    private static final List<TagKey<Structure>> AVOID = List.of(
            tag("outlaws_hideout"), tag("trappers_lodge"), tag("pillagers_outpost"), tag("woodland_mansion"),
            tag("trail_ruins"), tag("desert_temple"), tag("jungle_temple"), tag("ancient_city"),
            tag("pirates_cove"), tag("treasure_chest"), tag("ocean_monument"), tag("trial_chamber"),
            tag("ruined_portal"), tag("bone_temple"), tag("bastion_fortress"), tag("nether_fortress"));

    private GuideStructureAvoidance() {
    }

    private static TagKey<Structure> tag(String path) {
        return TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath(TerrainLab.MODID, "locate/" + path));
    }

    public static boolean isNearDestination(LevelAccessor level, BlockPos pos) {
        return findNearbyDestination(level, pos) != null;
    }

    /**
     * The box of a guide-destination piece near pos (or of the whole structure
     * if its start chunk can't be read), or null if there is none.
     */
    public static @Nullable BoundingBox findNearbyDestination(LevelAccessor level, BlockPos pos) {
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        BoundingBox zone = new BoundingBox(
                pos.getX() - AVOID_RADIUS, pos.getY() - VERTICAL_MARGIN, pos.getZ() - AVOID_RADIUS,
                pos.getX() + AVOID_RADIUS, pos.getY() + VERTICAL_MARGIN, pos.getZ() + AVOID_RADIUS);

        // WorldGenRegion throws for chunks outside its working set (even with
        // require=false); this chunk's references cover every overlapping structure.
        if (level instanceof WorldGenRegion) {
            return scanChunk(level, registry, level.getChunk(pos), zone, false);
        }

        int minCx = zone.minX() >> 4, maxCx = zone.maxX() >> 4;
        int minCz = zone.minZ() >> 4, maxCz = zone.maxZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;   // never load a chunk for this check
                }
                BoundingBox hit = scanChunk(level, registry, level.getChunk(cx, cz), zone, true);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static @Nullable BoundingBox scanChunk(LevelAccessor level, Registry<Structure> registry,
                                                   ChunkAccess chunk, BoundingBox zone, boolean canReadStarts) {
        for (Map.Entry<Structure, LongSet> entry : chunk.getAllReferences().entrySet()) {
            if (entry.getValue().isEmpty() || !isDestination(registry, entry.getKey())) {
                continue;
            }
            for (long startChunk : entry.getValue()) {
                BoundingBox hit = nearPiece(level, entry.getKey(), new ChunkPos(startChunk), zone, canReadStarts);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    private static boolean isDestination(Registry<Structure> registry, Structure structure) {
        Holder<Structure> holder = registry.wrapAsHolder(structure);
        for (TagKey<Structure> tag : AVOID) {
            if (holder.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable BoundingBox nearPiece(LevelAccessor level, Structure structure, ChunkPos startChunk,
                                                   BoundingBox zone, boolean canReadStarts) {
        StructureStart start = canReadStarts && level.hasChunk(startChunk.x, startChunk.z)
                ? level.getChunk(startChunk.x, startChunk.z).getStartForStructure(structure)
                : null;
        if (start == null || !start.isValid()) {
            // Start chunk unreadable: the structure overlaps a nearby chunk, so treat it as near.
            return new BoundingBox(startChunk.getMinBlockX(), zone.minY(), startChunk.getMinBlockZ(),
                    startChunk.getMaxBlockX(), zone.maxY(), startChunk.getMaxBlockZ());
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece.getBoundingBox().intersects(zone)) {
                return piece.getBoundingBox();
            }
        }
        return null;
    }

    /** Walks a guide away from any destination it has strayed near. */
    public static final class AvoidGoal extends Goal {

        private final AbstractTrailTraderEntity guide;
        private @Nullable Vec3 target;
        private int nextCheck;

        public AvoidGoal(AbstractTrailTraderEntity guide) {
            this.guide = guide;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.guide.tickCount < this.nextCheck) {
                return false;
            }
            this.nextCheck = this.guide.tickCount + CHECK_INTERVAL;

            BoundingBox danger = findNearbyDestination(this.guide.level(), this.guide.blockPosition());
            if (danger == null) {
                return false;
            }
            Vec3 centre = Vec3.atCenterOf(danger.getCenter());
            this.target = DefaultRandomPos.getPosAway(this.guide, 24, 7, centre);
            return this.target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.guide.getNavigation().isDone();
        }

        @Override
        public void start() {
            this.guide.setWanderTarget(null);
            if (this.target != null) {
                this.guide.getNavigation().moveTo(this.target.x, this.target.y, this.target.z, 0.5);
            }
            this.nextCheck = this.guide.tickCount; // check again as soon as this leg ends
        }
    }
}
