package com.terrainlab.village.work;

import com.google.common.collect.ImmutableMap;
import com.terrainlab.village.VillagerWorkConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Base for the profession work behaviours. Instances are per villager (the work package is built per
 * brain), so instance fields hold that villager's transient run state; persisted cooldowns live in
 * {@link com.terrainlab.village.VillagerWorkState}.
 * <p>
 * A run starts only while {@link #activity} is active, the villager has a loaded job site and no walk
 * target (so it never cuts off another walk), and {@link #plan} finds something to do. Movement goes
 * through WALK_TARGET / LOOK_TARGET so the core MoveToTargetSink / LookAtTargetSink do the walking.
 * Each walk leg times out after {@link VillagerWorkConstants#TRIP_TIMEOUT_TICKS}. The brain does not
 * stop behaviours when the scheduled activity changes, so {@link #canStillUse} checks it.
 */
public abstract class AbstractVillagerWorkBehavior extends Behavior<Villager> {

    private static final int MAX_RUN_TICKS = 24000;

    private final ResourceKey<VillagerProfession> profession;
    private final Activity activity;

    private long nextAttempt;
    private boolean finished;
    private long legStart;
    private @Nullable Object walkGoal;
    private boolean hadCantReach;
    private @Nullable Item heldMarker;

    protected AbstractVillagerWorkBehavior(ResourceKey<VillagerProfession> profession, Activity activity) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT
        ), MAX_RUN_TICKS);
        this.profession = profession;
        this.activity = activity;
    }

    // ---------------------------------------------------------------- subclass hooks

    /** Decide what to do; return false when there is nothing (the behaviour then rests for {@link #retryDelay}). */
    protected abstract boolean plan(ServerLevel level, Villager body, BlockPos jobSite, long now);

    /** One tick of the planned work. Call {@link #finish} when done. */
    protected abstract void work(ServerLevel level, Villager body, long now);

    /** Clean-up after a run (chest lid, held item...). */
    protected void cleanup(ServerLevel level, Villager body, long now) {
    }

    protected int retryDelay() {
        return VillagerWorkConstants.IDLE_RETRY_TICKS;
    }

    protected int cooldownAfterRun() {
        return 40;
    }

    // ---------------------------------------------------------------- Behavior

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager body) {
        long now = level.getGameTime();
        if (now < nextAttempt) {
            return false;
        }
        if (!isEligible(body) || VillagerWork.isBusy(body)) {
            return false; // one work trip at a time (the others would fight over WALK_TARGET)
        }
        var site = VillagerWork.jobSite(body, level);
        if (site.isEmpty() || !plan(level, body, site.get(), now)) {
            nextAttempt = now + retryDelay();
            return false;
        }
        return true;
    }

    private boolean isEligible(Villager body) {
        return !body.isBaby() && body.isAlive() && !body.isSleeping() && !body.isTrading()
                && body.getVillagerData().profession().is(profession)
                && body.getBrain().isActive(activity);
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        finished = false;
        walkGoal = null;
        hadCantReach = body.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return !finished && isEligible(body);
    }

    @Override
    protected void tick(ServerLevel level, Villager body, long timestamp) {
        work(level, body, timestamp);
    }

    @Override
    protected void stop(ServerLevel level, Villager body, long timestamp) {
        cleanup(level, body, timestamp);
        restoreHand(body);
        Brain<Villager> brain = body.getBrain();
        if (walkGoal != null) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        // Our own failed paths must not count toward vanilla's "job site unreachable" timer.
        if (!hadCantReach) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
        walkGoal = null;
        nextAttempt = Math.max(nextAttempt,
                timestamp + (finished ? cooldownAfterRun() : VillagerWorkConstants.FAILED_RETRY_TICKS));
        finished = false;
    }

    // ---------------------------------------------------------------- helpers for subclasses

    protected final void finish() {
        finished = true;
    }

    /** Ends the run as a failure (longer retry delay). */
    protected final void fail(Villager body, long now) {
        finished = true;
        nextAttempt = now + VillagerWorkConstants.FAILED_RETRY_TICKS;
    }

    /** Pushes the next start attempt back (used by plan/work for custom rests). */
    protected final void restUntil(long tick) {
        nextAttempt = Math.max(nextAttempt, tick);
    }

    /** Starts a new walk leg (resets the leg timer). */
    protected final void beginLeg(long now) {
        legStart = now;
    }

    protected final boolean legTimedOut(long now) {
        return now - legStart > VillagerWorkConstants.TRIP_TIMEOUT_TICKS;
    }

    /** Keeps a walk target on {@code pos}, re-asserting it if another behaviour replaced or cleared it. */
    protected final void walkTo(Villager body, BlockPos pos, int closeEnough) {
        walkGoal = pos;
        Brain<Villager> brain = body.getBrain();
        boolean ours = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(t -> t.getTarget().currentBlockPosition().equals(pos))
                .orElse(false);
        if (!ours) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, VillagerWorkConstants.WALK_SPEED, closeEnough));
        }
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(pos));
    }

    protected final void walkTo(Villager body, Entity target, int closeEnough) {
        walkGoal = target;
        Brain<Villager> brain = body.getBrain();
        boolean ours = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(t -> t.getTarget() instanceof EntityTracker et && et.getEntity() == target)
                .orElse(false);
        if (!ours) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, VillagerWorkConstants.WALK_SPEED, closeEnough));
        }
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
    }

    /**
     * Holds the villager in place: any walk target set by another behaviour (e.g. vanilla's walk back to
     * the job site) is replaced by the villager's current spot, and the villager looks at {@code look}.
     */
    protected final void stayAndLook(Villager body, Vec3 look) {
        Brain<Villager> brain = body.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            BlockPos here = body.blockPosition();
            walkGoal = here;
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(here, VillagerWorkConstants.WALK_SPEED, 1));
        }
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(look));
        body.getLookControl().setLookAt(look.x, look.y, look.z);
        if (walkGoal == null) {
            walkGoal = Boolean.TRUE; // we own LOOK_TARGET, clear it on stop
        }
    }

    /** Puts {@code display} in the empty main hand (purely visual); restored on stop. */
    protected final void holdItem(Villager body, ItemStack display) {
        if (body.getMainHandItem().isEmpty() && !body.isTrading()) {
            body.setItemSlot(EquipmentSlot.MAINHAND, display.copyWithCount(1));
            heldMarker = display.getItem();
        }
    }

    protected final void restoreHand(Villager body) {
        if (heldMarker != null && body.getMainHandItem().is(heldMarker) && !body.isTrading()) {
            body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        heldMarker = null;
    }
}
