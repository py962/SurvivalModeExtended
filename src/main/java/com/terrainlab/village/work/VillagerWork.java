package com.terrainlab.village.work;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.village.VillageChestService;
import com.terrainlab.village.VillageMembershipService;
import com.terrainlab.village.VillagerWorkConstants;
import com.terrainlab.village.VillagerWorkState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Predicate;

/** Shared helpers for the profession work behaviours: job site, bell, chest, inventory and range checks. */
public final class VillagerWork {

    private VillagerWork() {
    }

    /** The villager's job site, if it is in this dimension and its chunk is loaded. */
    public static Optional<BlockPos> jobSite(Villager villager, ServerLevel level) {
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || !site.get().dimension().equals(level.dimension())) {
            return Optional.empty();
        }
        BlockPos pos = site.get().pos();
        return level.isLoaded(pos) ? Optional.of(pos) : Optional.empty();
    }

    /** The villager's village bell: its MEETING_POINT memory, else the bell whose village contains the job site. */
    public static Optional<BlockPos> bell(Villager villager, ServerLevel level, BlockPos jobSite) {
        Optional<GlobalPos> meeting = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        if (meeting.isPresent() && meeting.get().dimension().equals(level.dimension())) {
            return Optional.of(meeting.get().pos());
        }
        return VillageMembershipService.findMemberBell(level, jobSite);
    }

    /** The village food chest for this villager (loaded and still a chest), or empty. */
    public static Optional<BlockPos> chest(Villager villager, ServerLevel level) {
        Optional<BlockPos> site = jobSite(villager, level);
        if (site.isEmpty()) {
            return Optional.empty();
        }
        return bell(villager, level, site.get()).flatMap(bell -> VillageChestService.findChest(level, bell));
    }

    /** Bell for chest transfers (same resolution as {@link #chest}). */
    public static Optional<BlockPos> chestBell(Villager villager, ServerLevel level) {
        return jobSite(villager, level).flatMap(site -> bell(villager, level, site));
    }

    /** Inside the work area: {@link VillagerWorkConstants#RANGE} horizontally (circle), ±RANGE_VERTICAL. */
    public static boolean inWorkArea(BlockPos jobSite, BlockPos pos) {
        int dx = pos.getX() - jobSite.getX();
        int dz = pos.getZ() - jobSite.getZ();
        int dy = pos.getY() - jobSite.getY();
        int r = VillagerWorkConstants.RANGE;
        return dx * dx + dz * dz <= r * r && Math.abs(dy) <= VillagerWorkConstants.RANGE_VERTICAL;
    }

    public static boolean inWorkArea(BlockPos jobSite, Vec3 pos) {
        return inWorkArea(jobSite, BlockPos.containing(pos));
    }

    /** Close enough to act on a block/point: horizontal distance within reach and at most 3 blocks of height difference. */
    public static boolean withinReach(Villager villager, Vec3 target) {
        double dx = villager.getX() - target.x;
        double dz = villager.getZ() - target.z;
        double reach = VillagerWorkConstants.ACT_REACH;
        return dx * dx + dz * dz <= reach * reach && Math.abs(villager.getY() - target.y) <= 3.0D;
    }

    // ---------------------------------------------------------------- state

    public static VillagerWorkState state(Villager villager) {
        return villager.getData(ModAttachments.VILLAGER_WORK_STATE.get());
    }

    public static void setState(Villager villager, VillagerWorkState state) {
        villager.setData(ModAttachments.VILLAGER_WORK_STATE.get(), state);
    }

    /** True while one of this mod's work behaviours is running (other movement overrides should back off). */
    public static boolean isBusy(Villager villager) {
        for (BehaviorControl<? super Villager> behavior : villager.getBrain().getRunningBehaviors()) {
            if (behavior instanceof AbstractVillagerWorkBehavior) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- inventory

    public static int count(SimpleContainer inv, Predicate<ItemStack> filter) {
        int total = 0;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public static int freeSlots(SimpleContainer inv) {
        int free = 0;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (inv.getItem(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** Removes up to {@code amount} items matching {@code filter}; returns how many were removed. */
    public static int remove(SimpleContainer inv, Predicate<ItemStack> filter, int amount) {
        int removed = 0;
        for (int slot = inv.getContainerSize() - 1; slot >= 0 && removed < amount; slot--) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                int n = Math.min(amount - removed, stack.getCount());
                stack.shrink(n);
                if (stack.isEmpty()) {
                    inv.setItem(slot, ItemStack.EMPTY);
                }
                removed += n;
            }
        }
        if (removed > 0) {
            inv.setChanged();
        }
        return removed;
    }

    /** Adds {@code stack} to the villager's inventory; what does not fit is dropped at its feet. */
    public static void giveOrDrop(Villager villager, ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack rest = villager.getInventory().addItem(stack);
        if (!rest.isEmpty()) {
            villager.spawnAtLocation(level, rest);
        }
    }

    // ---------------------------------------------------------------- chest lid

    /** Opens (or closes) the chest lid for nearby players, with the vanilla sound. */
    public static void setChestOpen(ServerLevel level, BlockPos chestPos, boolean open) {
        if (!level.isLoaded(chestPos)) {
            return;
        }
        BlockState state = level.getBlockState(chestPos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return;
        }
        level.blockEvent(chestPos, state.getBlock(), 1, open ? 1 : 0);
        level.playSound(null, chestPos, open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS,
                0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }
}
