package com.terrainlab.temperature;

import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ambient heat contribution at a position from three sources of different cost:
 * <ul>
 *   <li>Indexed sources (lit campfires, magma, player-placed lava) within HeatFloodFill.RADIUS, with an
 *   enclosure factor computed lazily on first use and cached in the index.</li>
 *   <li>Natural lava: uncached straight-line falloff over a small cube.</li>
 *   <li>Fire: same as natural lava; never indexed because it changes constantly.</li>
 * </ul>
 * Never reads blocks in unloaded chunks (see HeatChunks). Callable from the render thread (client index) and the
 * server thread (server index); HeatSourceIndex is synchronized and returns snapshot lists.
 */
public final class HeatContributionService {

    private static final int NATURAL_LAVA_RADIUS = 4; // blocks
    private static final double NATURAL_LAVA_DECAY_PER_BLOCK = 3.5;

    private static final int FIRE_RADIUS = 3; // blocks
    private static final double FIRE_DECAY_PER_BLOCK = 7.0;

    private HeatContributionService() {
    }

    /** Total heat contribution at pos. */
    public static double getContribution(LevelReader level, BlockPos pos) {
        HeatSourceIndex index = (level instanceof ServerLevel serverLevel)
                ? serverLevel.getData(ModAttachments.HEAT_SOURCE_INDEX.get())
                : com.terrainlab.client.ClientHeatSourceIndex.get();

        // The radius also covers the natural-lava cube (corner ~6.9 blocks away), for de-duplication.
        List<HeatSourceData> nearby = index.near(pos, HeatFloodFill.RADIUS);

        double total = indexedContribution(level, index, nearby, pos);
        total += naturalLavaContribution(level, nearby, pos);
        total += fireContribution(level, pos);
        return total;
    }

    private static double indexedContribution(
            LevelReader level,
            HeatSourceIndex index,
            List<HeatSourceData> nearby,
            BlockPos pos
    ) {
        double total = 0.0;

        for (HeatSourceData data : nearby) {
            BlockPos sourcePos = data.sourcePos();

            if (!HeatChunks.isLoaded(level, sourcePos)) {
                continue;
            }

            if (!HeatSourceBlocks.isHeatSource(level.getBlockState(sourcePos))) {
                // Self-heal: the source was extinguished, broken or removed.
                if (index.remove(sourcePos) && level instanceof ServerLevel serverLevel) {
                    HeatSourceSyncService.broadcastRemoval(serverLevel, sourcePos);
                }
                continue;
            }

            HeatSourceData usable = data;
            if (!data.hasEnclosure()) {
                HeatFloodFill.Enclosure enclosure = HeatFloodFill.computeEnclosure(level, sourcePos);
                usable = data.withEnclosure(enclosure.factor());
                if (enclosure.complete()) {
                    index.replaceIfUnchanged(data, usable);
                }
            }

            total += usable.contributionAt(level, pos);
        }

        return total;
    }

    /** Natural lava: live straight-line falloff, skipping indexed lava to avoid double counting. */
    private static double naturalLavaContribution(
            LevelReader level,
            List<HeatSourceData> nearbyIndexed,
            BlockPos pos
    ) {
        Set<BlockPos> indexedPositions = new HashSet<>();
        for (HeatSourceData data : nearbyIndexed) {
            indexedPositions.add(data.sourcePos());
        }

        boolean allLoaded = HeatChunks.areaLoaded(level,
                pos.getX() - NATURAL_LAVA_RADIUS, pos.getZ() - NATURAL_LAVA_RADIUS,
                pos.getX() + NATURAL_LAVA_RADIUS, pos.getZ() + NATURAL_LAVA_RADIUS);

        double total = 0.0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -NATURAL_LAVA_RADIUS; dx <= NATURAL_LAVA_RADIUS; dx++) {
            for (int dz = -NATURAL_LAVA_RADIUS; dz <= NATURAL_LAVA_RADIUS; dz++) {
                if (!allLoaded && !HeatChunks.isLoaded(level,
                        SectionPos.blockToSectionCoord(pos.getX() + dx),
                        SectionPos.blockToSectionCoord(pos.getZ() + dz))) {
                    continue;
                }
                for (int dy = -NATURAL_LAVA_RADIUS; dy <= NATURAL_LAVA_RADIUS; dy++) {
                    cursor.setWithOffset(pos, dx, dy, dz);

                    if (indexedPositions.contains(cursor)) {
                        continue; // counted through the index
                    }

                    var fluidType = level.getBlockState(cursor).getFluidState().getType();
                    if (fluidType != Fluids.LAVA && fluidType != Fluids.FLOWING_LAVA) {
                        continue;
                    }

                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double contribution = HeatSourceBlocks.LAVA_STRENGTH
                            - NATURAL_LAVA_DECAY_PER_BLOCK * distance;
                    total += Math.max(0.0, contribution);
                }
            }
        }

        return total;
    }

    private static double fireContribution(LevelReader level, BlockPos pos) {
        boolean allLoaded = HeatChunks.areaLoaded(level,
                pos.getX() - FIRE_RADIUS, pos.getZ() - FIRE_RADIUS,
                pos.getX() + FIRE_RADIUS, pos.getZ() + FIRE_RADIUS);

        double total = 0.0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -FIRE_RADIUS; dx <= FIRE_RADIUS; dx++) {
            for (int dz = -FIRE_RADIUS; dz <= FIRE_RADIUS; dz++) {
                if (!allLoaded && !HeatChunks.isLoaded(level,
                        SectionPos.blockToSectionCoord(pos.getX() + dx),
                        SectionPos.blockToSectionCoord(pos.getZ() + dz))) {
                    continue;
                }
                for (int dy = -FIRE_RADIUS; dy <= FIRE_RADIUS; dy++) {
                    cursor.setWithOffset(pos, dx, dy, dz);

                    if (!level.getBlockState(cursor).is(Blocks.FIRE)) {
                        continue;
                    }

                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double contribution = HeatSourceBlocks.FIRE_STRENGTH
                            - FIRE_DECAY_PER_BLOCK * distance;
                    total += Math.max(0.0, contribution);
                }
            }
        }

        return total;
    }
}
