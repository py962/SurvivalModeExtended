package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Optional;

/**
 * Tier-3+ daily routine for adult villagers. In 1.21.11 villager schedules are datapack-driven
 * environment attributes with no per-village hook, so this instead re-issues a navigation command
 * on each staggered read toward the villager's claimed JOB_SITE, MEETING_POINT or HOME for the
 * current phase. Wander phases issue nothing and leave vanilla AI in control; villagers without
 * the relevant memory are left alone.
 */
public final class VillagerScheduleService {

    private VillagerScheduleService() {
    }

    // Same speed vanilla's villager goal packages use.
    private static final double WALK_SPEED = 0.5D;

    private enum Phase {
        WANDER, WORK, GATHER, SLEEP
    }

    /**
     * Phase transitions by unwarped tick of day. Sleep wraps past midnight until tick 10.
     */
    private static final long[] TRANSITION_TICKS = {10, 2000, 3000, 6000, 9000, 10000, 11000, 12000, 13000, 14000};
    private static final Phase[] TRANSITION_PHASES = {
            Phase.WANDER, Phase.WORK, Phase.WORK, Phase.GATHER, Phase.GATHER,
            Phase.WORK, Phase.WORK, Phase.WANDER, Phase.GATHER, Phase.SLEEP
    };

    public static void update(Villager villager, ServerLevel level) {
        if (villager.isBaby() || villager.isSleeping()) {
            return;
        }

        BlockPos pos = villager.blockPosition();
        Optional<BlockPos> memberBell = VillageMembershipService.findMemberBell(level, pos);
        if (memberBell.isEmpty()) {
            return;
        }

        VillageId id = new VillageId(level.dimension(), memberBell.get());
        VillageRecord record = VillageSavedData.get(level).getOrCreate(id);
        if (record.getTier().level() < VillageTier.TIER_3_VILLAGE.level()) {
            return;
        }

        Phase phase = phaseAt(com.terrainlab.season.SolarTime.solarTimeOfDay(level));

        // A running work behaviour (village.work) is moving the villager on its own trip.
        if (com.terrainlab.village.work.VillagerWork.isBusy(villager)) {
            return;
        }

        switch (phase) {
            case WANDER -> {
                // No command: vanilla idle/wander AI stays in control.
            }
            case WORK -> moveTowardMemory(villager, MemoryModuleType.JOB_SITE, level);
            case GATHER -> moveTowardMemory(villager, MemoryModuleType.MEETING_POINT, level);
            case SLEEP -> moveTowardMemory(villager, MemoryModuleType.HOME, level);
        }
    }

    private static Phase phaseAt(long tickOfDay) {
        // Ticks before the first transition belong to the previous day's Sleep phase.
        Phase current = TRANSITION_PHASES[TRANSITION_PHASES.length - 1];
        for (int i = 0; i < TRANSITION_TICKS.length; i++) {
            if (TRANSITION_TICKS[i] <= tickOfDay) {
                current = TRANSITION_PHASES[i];
            } else {
                break;
            }
        }
        return current;
    }

    private static void moveTowardMemory(Villager villager, MemoryModuleType<GlobalPos> memoryType, ServerLevel level) {
        Optional<GlobalPos> target = villager.getBrain().getMemory(memoryType);
        if (target.isEmpty()) {
            return;
        }
        GlobalPos globalPos = target.get();
        if (!globalPos.dimension().equals(level.dimension())) {
            return;
        }
        BlockPos p = globalPos.pos();
        villager.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, WALK_SPEED);
    }
}
