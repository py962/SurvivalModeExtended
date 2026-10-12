package com.terrainlab.worldtime;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Lets furnaces near online players catch up on the time skipped by sleeping, by running extra
 * furnace ticks spread over the following server ticks.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class FurnaceCatchUpHandler {

    // Chunk radius around each online player scanned for furnaces; roughly the simulation distance.
    private static final int SCAN_RADIUS_CHUNKS = 12;

    // Simulated furnace ticks per real tick, per furnace.
    private static final int CATCHUP_BUDGET_PER_TICK = 200;

    // Cap on ticks queued by a single skip.
    private static final int MAX_QUEUED_CATCHUP_TICKS = 24000;

    // Furnaces with queued catch-up work.
    private static final Set<BlockEntity> catchingUp =
            Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        long oldTime = level.getDayTime();
        long newTime = event.getNewTime();
        long skipped = Math.min(MAX_QUEUED_CATCHUP_TICKS, Math.max(0, newTime - oldTime));
        if (skipped <= 0) return;

        for (AbstractFurnaceBlockEntity furnace : findNearbyFurnaces(level)) {
            int pending = furnace.getData(ModAttachments.FURNACE_PENDING_CATCHUP.get());
            long updated = Math.min(Integer.MAX_VALUE, pending + skipped);
            furnace.setData(ModAttachments.FURNACE_PENDING_CATCHUP.get(), (int) updated);
            catchingUp.add(furnace);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (catchingUp.isEmpty()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        catchingUp.removeIf(be -> {
            if (be.isRemoved()) return true;
            if (be.getLevel() != level) return false; // processed by its own level's tick
            if (!(be instanceof AbstractFurnaceBlockEntity furnace)) return true;

            int pending = furnace.getData(ModAttachments.FURNACE_PENDING_CATCHUP.get());
            if (pending <= 0) return true;

            int steps = Math.min(pending, CATCHUP_BUDGET_PER_TICK);
            BlockPos pos = furnace.getBlockPos();
            for (int i = 0; i < steps; i++) {
                AbstractFurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
            }
            pending -= steps;
            furnace.setData(ModAttachments.FURNACE_PENDING_CATCHUP.get(), pending);
            return pending <= 0;
        });
    }

    // Scans loaded chunks around each player, not every loaded chunk; furnaces far from all players
    // don't catch up.
    private static List<AbstractFurnaceBlockEntity> findNearbyFurnaces(ServerLevel level) {
        List<AbstractFurnaceBlockEntity> result = new ArrayList<>();
        Set<Long> visitedChunks = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            ChunkPos center = new ChunkPos(player.blockPosition());
            for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
                for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                    int chunkX = center.x + dx;
                    int chunkZ = center.z + dz;
                    long key = ChunkPos.asLong(chunkX, chunkZ);
                    if (!visitedChunks.add(key)) continue;
                    if (!level.hasChunk(chunkX, chunkZ)) continue;

                    LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof AbstractFurnaceBlockEntity furnace) {
                            result.add(furnace);
                        }
                    }
                }
            }
        }
        return result;
    }
}
