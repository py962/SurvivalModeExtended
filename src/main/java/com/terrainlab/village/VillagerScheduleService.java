package com.terrainlab.village;

import com.terrainlab.hearth.BrainScheduleOverride;
import com.terrainlab.hearth.HearthService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Villager daily routine on solar time, for every villager; the hearth gathering is tier 3+ only. */
public final class VillagerScheduleService {

    private VillagerScheduleService() {
    }

    // Same speed vanilla's villager goal packages use.
    private static final double WALK_SPEED = 0.5D;

    private enum Phase {
        MORNING, WORK, MEET, EVENING, HEARTH, SLEEP
    }

    /**
     * Phase transitions by unwarped tick of day. Sleep wraps past midnight until tick 10.
     */
    private static final long[] TRANSITION_TICKS = {10, 2000, 6000, 7000, 11000, 12000, 14000};
    private static final Phase[] TRANSITION_PHASES = {
            Phase.MORNING, Phase.WORK, Phase.MEET, Phase.WORK, Phase.EVENING, Phase.HEARTH, Phase.SLEEP
    };

    public static void update(Villager villager, ServerLevel level) {
        if (villager.isBaby()) {
            setOverride(villager, babyActivityAt(com.terrainlab.season.SolarTime.solarTimeOfDay(level)));
            return;
        }

        Optional<BlockPos> memberBell = VillageMembershipService.findMemberBell(level, villager.blockPosition());
        boolean gathers = memberBell.isPresent() && VillageSavedData.get(level)
                .getOrCreate(new VillageId(level.dimension(), memberBell.get()))
                .getTier().level() >= VillageTier.TIER_3_VILLAGE.level();

        Phase phase = phaseAt(com.terrainlab.season.SolarTime.solarTimeOfDay(level));
        // Frightened villagers skip the evening and go to bed.
        if ((phase == Phase.EVENING || phase == Phase.HEARTH)
                && VillagerFearService.isFearful(villager, level.getGameTime())) {
            phase = Phase.SLEEP;
        }
        // Below tier 3, or outside any village, there is no gathering: bed instead.
        if (phase == Phase.HEARTH && !gathers) {
            phase = Phase.SLEEP;
        }
        Optional<BlockPos> hearth = phase == Phase.HEARTH
                ? HearthService.findHearth(level, memberBell.get())
                : Optional.empty();

        // Set even while asleep: leaving REST is what wakes villagers in the morning.
        setOverride(villager, switch (phase) {
            case MORNING, EVENING -> Activity.IDLE;
            case WORK -> Activity.WORK;
            case MEET -> Activity.MEET;
            case HEARTH -> hearth.isPresent() ? Activity.IDLE : Activity.MEET;
            case SLEEP -> Activity.REST;
        });
        if (phase != Phase.HEARTH) {
            HearthService.standUp(villager);
        }

        if (villager.isSleeping()) {
            return;
        }
        if (com.terrainlab.village.work.VillagerWork.isBusy(villager)) {
            return;
        }

        switch (phase) {
            case MORNING, EVENING -> {
            }
            case WORK -> moveTowardMemory(villager, MemoryModuleType.JOB_SITE, level);
            case MEET -> moveTowardMemory(villager, MemoryModuleType.MEETING_POINT, level);
            case HEARTH -> {
                if (hearth.isPresent()) {
                    HearthService.gather(villager, level, hearth.get());
                } else {
                    moveTowardMemory(villager, MemoryModuleType.MEETING_POINT, level);
                }
            }
            case SLEEP -> moveTowardMemory(villager, MemoryModuleType.HOME, level);
        }
    }

    /**
     * Re-issues the hearth gathering every second between the five-second schedule reads, so idle
     * strolling and chatting can't pull villagers away from the hearth.
     */
    public static void refreshGathering(Villager villager, ServerLevel level) {
        if (villager.isBaby() || villager.isSleeping()) return;
        if (phaseAt(com.terrainlab.season.SolarTime.solarTimeOfDay(level)) != Phase.HEARTH) return;
        if (VillagerFearService.isFearful(villager, level.getGameTime())) return;
        if (com.terrainlab.village.work.VillagerWork.isBusy(villager)) return;
        Optional<BlockPos> bell = VillageMembershipService.findMemberBell(level, villager.blockPosition());
        if (bell.isEmpty()) return;
        VillageRecord record = VillageSavedData.get(level).getOrCreate(new VillageId(level.dimension(), bell.get()));
        if (record.getTier().level() < VillageTier.TIER_3_VILLAGE.level()) return;
        HearthService.findHearth(level, bell.get()).ifPresent(hearth -> HearthService.gather(villager, level, hearth));
    }

    // Vanilla's baby timings (idle, play, idle, play, rest), on solar time.
    private static Activity babyActivityAt(long tickOfDay) {
        if (tickOfDay < 10 || tickOfDay >= 12000) return Activity.REST;
        if (tickOfDay < 3000) return Activity.IDLE;
        if (tickOfDay < 6000) return Activity.PLAY;
        if (tickOfDay < 10000) return Activity.IDLE;
        return Activity.PLAY;
    }

    private static void setOverride(Villager villager, @Nullable Activity activity) {
        ((BrainScheduleOverride) villager.getBrain()).terrainlab$setScheduleOverride(activity);
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
