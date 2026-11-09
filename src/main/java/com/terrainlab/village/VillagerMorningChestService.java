package com.terrainlab.village;

import com.terrainlab.registry.ModAttachments;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Every morning, at the start of the villagers' first wander (solar ticks 10-2000, before work),
 * each adult walks to its bell, where the village food chest is, so a hungry villager gets the
 * chance to eat ({@link VillagerChestMealService} runs right after this on the same read). The
 * trip counts as done once a read finds the villager within reach of the bell; it is asked again
 * on each 5-second read until then, and given up when the morning wander ends.
 */
public final class VillagerMorningChestService {

    private static final long MORNING_START = 10L;
    private static final long MORNING_END = 2000L;
    /** A trip less than half a day ago counts as this morning's. */
    private static final long TRIP_COOLDOWN_TICKS = 12000L;
    private static final float WALK_SPEED = 0.5F;
    private static final int CLOSE_ENOUGH = 2;

    private VillagerMorningChestService() {
    }

    public static void update(Villager villager, ServerLevel level, long gameTime) {
        if (villager.isBaby() || villager.isSleeping() || villager.isTrading() || !villager.isAlive()) return;

        long tod = com.terrainlab.season.SolarTime.solarTimeOfDay(level);
        if (tod < MORNING_START || tod >= MORNING_END) return;

        long last = villager.getData(ModAttachments.VILLAGER_MORNING_CHEST_TICK.get());
        if (last >= 0L && gameTime - last < TRIP_COOLDOWN_TICKS) return;

        Optional<GlobalPos> meeting = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        if (meeting.isEmpty() || !meeting.get().dimension().equals(level.dimension())) return;
        BlockPos bell = meeting.get().pos();

        double dx = villager.getX() - (bell.getX() + 0.5D);
        double dz = villager.getZ() - (bell.getZ() + 0.5D);
        if (dx * dx + dz * dz <= VillagerChestMealService.BELL_REACH * VillagerChestMealService.BELL_REACH
                && Math.abs(villager.getY() - bell.getY()) <= 3.0D) {
            villager.setData(ModAttachments.VILLAGER_MORNING_CHEST_TICK.get(), gameTime);
            return;
        }

        if (com.terrainlab.village.work.VillagerWork.isBusy(villager)) return;
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(bell, WALK_SPEED, CLOSE_ENOUGH));
    }
}
