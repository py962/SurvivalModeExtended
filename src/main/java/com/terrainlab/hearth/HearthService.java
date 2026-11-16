package com.terrainlab.hearth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Evening gathering: villagers sit on free cushions near the village hearth, or stand around it. */
public final class HearthService {

    private HearthService() {
    }

    public static final int BELL_RADIUS = 96;
    public static final double SEAT_RADIUS = 12.0;
    private static final double SIT_REACH_SQ = 3.0 * 3.0;
    private static final int HEARTH_CLOSE_ENOUGH = 3;
    private static final float WALK_SPEED = 0.5F;
    private static final long CACHE_TICKS = 200L;

    private record Cached(long gameTime, @Nullable BlockPos hearth) {
    }

    private static final Map<GlobalPos, Cached> CACHE = new HashMap<>();

    /** Nearest lit hearth within BELL_RADIUS of the bell. */
    public static Optional<BlockPos> findHearth(ServerLevel level, BlockPos bell) {
        GlobalPos key = GlobalPos.of(level.dimension(), bell);
        long now = level.getGameTime();
        Cached cached = CACHE.get(key);
        if (cached == null || now - cached.gameTime() >= CACHE_TICKS || now < cached.gameTime()) {
            BlockPos found = level.getPoiManager().findClosest(
                    holder -> holder.value() == ModHearth.HEARTH_POI.get(),
                    pos -> isLitHearth(level, pos),
                    bell, BELL_RADIUS, PoiManager.Occupancy.ANY).orElse(null);
            cached = new Cached(now, found);
            CACHE.put(key, cached);
        }
        BlockPos hearth = cached.hearth();
        return hearth != null && isLitHearth(level, hearth) ? Optional.of(hearth) : Optional.empty();
    }

    public static BlockPos gatheringPoint(ServerLevel level, BlockPos bell) {
        return findHearth(level, bell).orElse(bell);
    }

    private static boolean isLitHearth(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof HearthBlock && state.getValue(CampfireBlock.LIT);
    }

    public static void gather(Villager villager, ServerLevel level, BlockPos hearth) {
        if (villager.getBrain().isActive(Activity.PANIC)) {
            standUp(villager);
            return;
        }
        Entity vehicle = villager.getVehicle();
        if (vehicle != null && HearthCompat.isCushion(vehicle)) {
            return;
        }
        Optional<? extends Entity> seat = nearestFreeCushion(level, hearth, villager);
        if (seat.isPresent()) {
            Entity cushion = seat.get();
            if (villager.distanceToSqr(cushion) <= SIT_REACH_SQ) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                villager.getNavigation().stop();
                villager.startRiding(cushion, true, true);
            } else {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(cushion.blockPosition(), WALK_SPEED, 0));
            }
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(hearth, WALK_SPEED, HEARTH_CLOSE_ENOUGH));
    }

    public static void standUp(Villager villager) {
        Entity vehicle = villager.getVehicle();
        if (vehicle != null && HearthCompat.isCushion(vehicle)) {
            villager.stopRiding();
        }
    }

    private static Optional<? extends Entity> nearestFreeCushion(ServerLevel level, BlockPos hearth, Villager villager) {
        List<? extends Entity> cushions = level.getEntitiesOfClass(HearthCompat.CUSHION_CLASS,
                new AABB(hearth).inflate(SEAT_RADIUS),
                c -> c.isAlive() && !c.isVehicle());
        return cushions.stream().min(Comparator.comparingDouble(villager::distanceToSqr));
    }
}
