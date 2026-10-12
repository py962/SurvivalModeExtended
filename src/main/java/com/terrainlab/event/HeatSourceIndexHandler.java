package com.terrainlab.event;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.temperature.HeatSourceData;
import com.terrainlab.temperature.HeatSourceBlocks;
import com.terrainlab.temperature.HeatSourceIndex;
import com.terrainlab.temperature.HeatSourceSyncService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Maintains the level-scoped HEAT_SOURCE_INDEX attachment: lit campfires, magma
 * blocks and player-placed lava, covering loaded chunks only. Natural lava and
 * fire are not indexed (see HeatContributionService); stale entries are removed
 * lazily when read.
 * <p>
 * Sources are indexed with their enclosure pending; HeatContributionService
 * computes it on first use. Placing or breaking a block invalidates cached
 * enclosures within 8 blocks.
 */
public final class HeatSourceIndexHandler {

    private HeatSourceIndexHandler() {
    }

    /** Indexes player-placed heat sources (lit campfires, magma). */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        indexIfHeatSource(level, event.getPos());
        invalidateEnclosureNear(level, event.getPos());
    }

    /** Drops a broken heat source and invalidates enclosures around the broken block. */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        runAfterThisTick(level, () -> {
            HeatSourceIndex index = level.getData(ModAttachments.HEAT_SOURCE_INDEX.get());
            if (index.contains(pos)) {
                indexIfHeatSource(level, pos);
            }
            invalidateEnclosureNear(level, pos);
        });
    }

    /**
     * Queues a task to run once the current tick has finished, after vanilla
     * has applied the interaction or removal that triggered the event.
     * {@code MinecraftServer#execute} would run it immediately on the server thread.
     */
    private static void runAfterThisTick(ServerLevel level, Runnable task) {
        MinecraftServer server = level.getServer();
        server.schedule(new TickTask(server.getTickCount(), task));
    }

    private static void invalidateEnclosureNear(ServerLevel level, BlockPos changed) {
        HeatSourceIndex index = level.getData(ModAttachments.HEAT_SOURCE_INDEX.get());
        for (HeatSourceData cleared : index.invalidateEnclosureNear(changed)) {
            HeatSourceSyncService.broadcastUpdate(level, cleared);
        }
    }

    /**
     * Re-indexes campfires that are lit or extinguished in place, and lava
     * placed from a bucket (not covered by the placement event).
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();

        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer)) {
            return;
        }

        BlockPos clickedPos = event.getPos();

        boolean isCampfireFamily =
                level.getBlockState(clickedPos).getBlock() instanceof CampfireBlock;

        boolean isLavaBucket =
                event.getItemStack().is(Items.LAVA_BUCKET);

        if (!isCampfireFamily && !isLavaBucket) {
            return;
        }

        // com.terrainlab.TerrainLab.LOGGER.info(
        //         "[HeatIndexDebug] onRightClickBlock fired pos={} isCampfireFamily={} isLavaBucket={} stateBeforeInteraction={}",
        //         clickedPos,
        //         isCampfireFamily,
        //         isLavaBucket,
        //         level.getBlockState(clickedPos)
        // );

        runAfterThisTick(serverLevel, () -> {
            if (isCampfireFamily) {
                indexIfHeatSource(serverLevel, clickedPos);
            }

            if (isLavaBucket) {
                indexPlacedLava(serverLevel, clickedPos);

                for (Direction direction : Direction.values()) {
                    indexPlacedLava(serverLevel, clickedPos.relative(direction));
                }
            }
        });
    }

    /**
     * Indexes campfires and magma already present when a chunk loads. Runs
     * immediately (reads only the chunk's own sections) so the sources are
     * indexed before the chunk is sent; no packets are sent here.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        HeatSourceIndex index = serverLevel.getData(ModAttachments.HEAT_SOURCE_INDEX.get());
        scanChunkForIndexableHeatSources(index, event.getChunk());
        reindexPlacedLava(index, event.getChunk());
    }

    /** Re-indexes player-placed lava recorded in the chunk, dropping entries that are no longer lava. */
    private static void reindexPlacedLava(HeatSourceIndex index, LevelChunk chunk) {
        List<Long> placed = chunk.getData(ModAttachments.PLACED_LAVA.get());
        if (placed.isEmpty()) {
            return;
        }
        List<Long> stillLava = new ArrayList<>(placed.size());
        for (long packed : placed) {
            BlockPos pos = BlockPos.of(packed);
            BlockState state = chunk.getBlockState(pos);
            double strength = HeatSourceBlocks.getStrength(state);
            if (strength > 0.0 && state.getFluidState().is(FluidTags.LAVA)) {
                index.put(pos, HeatSourceData.pending(pos, strength));
                stillLava.add(packed);
            }
        }
        if (stillLava.size() != placed.size()) {
            chunk.setData(ModAttachments.PLACED_LAVA.get(), List.copyOf(stillLava));
        }
    }

    /** Indexes lava a player has just placed and records it in its chunk for later reloads. */
    private static void indexPlacedLava(ServerLevel level, BlockPos pos) {
        indexIfHeatSource(level, pos);
        if (!level.getFluidState(pos).is(FluidTags.LAVA)
                || !level.getData(ModAttachments.HEAT_SOURCE_INDEX.get()).contains(pos)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        List<Long> placed = chunk.getData(ModAttachments.PLACED_LAVA.get());
        long packed = pos.asLong();
        if (!placed.contains(packed)) {
            List<Long> updated = new ArrayList<>(placed);
            updated.add(packed);
            chunk.setData(ModAttachments.PLACED_LAVA.get(), List.copyOf(updated));
        }
    }

    /** Limits both the server index and the client mirror to loaded chunks. */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        LevelAccessor level = event.getLevel();
        int chunkX = event.getChunk().getPos().x;
        int chunkZ = event.getChunk().getPos().z;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getData(ModAttachments.HEAT_SOURCE_INDEX.get()).removeChunk(chunkX, chunkZ);
        } else if (level.isClientSide()) {
            com.terrainlab.client.ClientHeatSourceIndex.get().removeChunk(chunkX, chunkZ);
        }
    }

    /** Sends a chunk's heat sources to a player right after the chunk itself. */
    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        HeatSourceIndex index = event.getLevel().getData(ModAttachments.HEAT_SOURCE_INDEX.get());
        HeatSourceSyncService.sendChunkTo(event.getPlayer(), index, event.getPos());
    }

    /**
     * Scans a chunk's sections for lit campfires and magma blocks, skipping
     * sections whose palette cannot contain either.
     */
    private static void scanChunkForIndexableHeatSources(HeatSourceIndex index, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int minSectionY = chunk.getMinSectionY();
        int chunkMinX = chunk.getPos().getMinBlockX();
        int chunkMinZ = chunk.getPos().getMinBlockZ();

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];

            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            if (!section.maybeHas(HeatSourceIndexHandler::isIndexableStaticHeatSource)) {
                continue;
            }

            int baseY = (minSectionY + sectionIndex) << 4;

            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        if (!isIndexableStaticHeatSource(state)) {
                            continue;
                        }
                        double strength = HeatSourceBlocks.getStrength(state);
                        if (strength <= 0.0) {
                            continue; // e.g. an unlit campfire
                        }
                        BlockPos pos = new BlockPos(chunkMinX + localX, baseY + localY, chunkMinZ + localZ);
                        index.put(pos, HeatSourceData.pending(pos, strength));
                    }
                }
            }
        }
    }

    private static boolean isIndexableStaticHeatSource(BlockState state) {
        return state.getBlock() instanceof CampfireBlock || state.is(Blocks.MAGMA_BLOCK);
    }

    /** Indexes the block if it currently provides heat, otherwise removes any stale entry. */
    private static void indexIfHeatSource(ServerLevel level, BlockPos pos) {
        HeatSourceIndex index =
                level.getData(ModAttachments.HEAT_SOURCE_INDEX.get());

        BlockState state = level.getBlockState(pos);
        double strength = HeatSourceBlocks.getStrength(state);

        if (strength <= 0.0) {
            if (index.remove(pos)) {
                HeatSourceSyncService.broadcastRemoval(level, pos);
            }
            return;
        }

        HeatSourceData data = HeatSourceData.pending(pos, strength);
        index.put(pos, data);

        HeatSourceSyncService.broadcastUpdate(level, data);
    }
}
