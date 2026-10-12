package com.terrainlab.event;

import com.terrainlab.block.FieldStoneBlock;
import com.terrainlab.block.RedstonePortalBlock;
import com.terrainlab.portal.RedstonePortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Drives Redstone Portal activation and deactivation from block-change notifications. The
 * event reports the changed position and its new state:
 * <ul>
 *   <li>If it is a Field Stone, its LIT state is synced to its power and the frame it
 *   borders is re-scanned.</li>
 *   <li>Otherwise (a lever, dust, or a broken frame block), adjacent Field Stones are
 *   re-checked and adjacent portal cells re-validated.</li>
 * </ul>
 */
public class RedstonePortalHandler {

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        // This handler's own setBlock calls re-fire this event synchronously; the mutating flag
        // covers the whole body to prevent unbounded recursion.
        if (RedstonePortalShape.isMutating()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        RedstonePortalShape.setMutating(true);
        try {
            BlockPos pos = event.getPos();
            Block block = event.getState().getBlock();

            if (block instanceof FieldStoneBlock) {
                updateLit(level, pos);
                RedstonePortalShape.recomputeFromFrame(level, pos);
                return;
            }

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                Block neighborBlock = level.getBlockState(neighbor).getBlock();
                if (neighborBlock instanceof FieldStoneBlock) {
                    updateLit(level, neighbor);
                    RedstonePortalShape.recomputeFromFrame(level, neighbor);
                } else if (neighborBlock instanceof RedstonePortalBlock) {
                    RedstonePortalShape.validatePortalCell(level, neighbor);
                }
            }
        } finally {
            RedstonePortalShape.setMutating(false);
        }
    }

    /** Syncs a Field Stone's LIT state to whether it is powered. */
    private static void updateLit(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean powered = level.getBestNeighborSignal(pos) > 0;
        if (state.getValue(FieldStoneBlock.LIT) != powered) {
            level.setBlock(pos, state.setValue(FieldStoneBlock.LIT, powered), 3);
        }
    }
}
