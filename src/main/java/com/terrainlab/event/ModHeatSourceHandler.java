package com.terrainlab.event;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.temperature.HeatFloodFill;
import com.terrainlab.temperature.HeatSourceBlocks;
import com.terrainlab.temperature.HeatSourceIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Maintains {@link HeatSourceIndex} on block changes: recomputes a source's flood fill when
 * a block becomes or stops being a heat source, or changes within an existing source's reach.
 *
 * <p>Not registered; superseded by {@link HeatSourceIndexHandler}.
 */
public class ModHeatSourceHandler {

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        HeatSourceIndex index = level.getData(ModAttachments.HEAT_SOURCE_INDEX.get());
        BlockState state = event.getState();

        boolean isSourceNow = HeatSourceBlocks.isHeatSource(state);
        boolean wasSource = index.all().containsKey(pos);

        if (isSourceNow && !wasSource) {
            recompute(level, index, pos, HeatSourceBlocks.getStrength(state));
        } else if (!isSourceNow && wasSource) {
            index.remove(pos);
        } else if (isSourceNow) {
            // Strength may have changed; recomputing is cheap.
            recompute(level, index, pos, HeatSourceBlocks.getStrength(state));
        }

        // Re-flood any existing source whose cube contains the changed position (walls alter its enclosure).
        for (BlockPos sourcePos : java.util.List.copyOf(index.all().keySet())) {
            if (sourcePos.equals(pos)) continue;
            var data = index.all().get(sourcePos);
            if (data != null
                    && Math.abs(sourcePos.getX() - pos.getX()) <= HeatFloodFill.RADIUS
                    && Math.abs(sourcePos.getY() - pos.getY()) <= HeatFloodFill.RADIUS
                    && Math.abs(sourcePos.getZ() - pos.getZ()) <= HeatFloodFill.RADIUS) {
                recompute(level, index, sourcePos, data.baseStrength());
            }
        }
    }

    private static void recompute(ServerLevel level, HeatSourceIndex index, BlockPos sourcePos, double strength) {
        index.put(sourcePos, HeatFloodFill.compute(level, sourcePos, strength));
    }
}
