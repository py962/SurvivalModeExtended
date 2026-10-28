
package com.terrainlab.event;

import com.terrainlab.block.FieldStoneBlock;
import com.terrainlab.block.RedstonePortalBlock;
import com.terrainlab.portal.RedstonePortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public final class RedstonePortalHandler {

    private RedstonePortalHandler() {
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (RedstonePortalShape.isMutating()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockPos pos = event.getPos();
        Block changedBlock = event.getState().getBlock();

        // Handle a Field Stone at the changed position directly.
        if (changedBlock instanceof FieldStoneBlock) {
            RedstonePortalShape.setMutating(true);
            try {
                updateLit(level, pos);
                RedstonePortalShape.recomputeFromFrame(level, pos);
            } finally {
                RedstonePortalShape.setMutating(false);
            }
            return;
        }

        // Ordinary block changes (including seasonal plant removal) should
        // be ignored unless an already-loaded adjacent chunk contains a
        // relevant portal block.
        boolean relevant = false;

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            Block neighborBlock = getLoadedBlock(level, neighbor);

            if (neighborBlock instanceof FieldStoneBlock
                    || neighborBlock instanceof RedstonePortalBlock) {
                relevant = true;
                break;
            }
        }

        if (!relevant) {
            return;
        }

        RedstonePortalShape.setMutating(true);
        try {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                Block neighborBlock = getLoadedBlock(level, neighbor);

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

    /**
     * Returns the block at a position only when its chunk is already loaded.
     * Never requests a chunk load.
     */
    private static Block getLoadedBlock(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                pos.getX() >> 4,
                pos.getZ() >> 4
        );

        if (chunk == null) {
            return null;
        }

        return chunk.getBlockState(pos).getBlock();
    }

    /**
     * Syncs a Field Stone's LIT state to whether it is powered.
     */
    private static void updateLit(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                pos.getX() >> 4,
                pos.getZ() >> 4
        );

        if (chunk == null) {
            return;
        }

        BlockState state = chunk.getBlockState(pos);

        if (!(state.getBlock() instanceof FieldStoneBlock)) {
            return;
        }

        boolean powered = level.getBestNeighborSignal(pos) > 0;

        if (state.getValue(FieldStoneBlock.LIT) != powered) {
            level.setBlock(pos, state.setValue(FieldStoneBlock.LIT, powered), 3);
        }
    }
}
