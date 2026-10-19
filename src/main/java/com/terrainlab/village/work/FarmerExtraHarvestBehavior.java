package com.terrainlab.village.work;

import com.terrainlab.village.VillagerWorkConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Farmer: harvests what vanilla's HarvestFarmland ignores, within the work area around the job site.
 * Mature pumpkins and melons (the fruit block next to an attached stem; the stem stays) and sugar cane
 * (everything from the 2nd block of a column up; the bottom block stays to regrow). Drops go straight
 * into the farmer's inventory (overflow is dropped). Needs mobGriefing, a village chest (the produce
 * is for it) and a free inventory slot. Up to {@link VillagerWorkConstants#FARMER_MAX_HARVESTS_PER_RUN}
 * blocks per run, nearest first.
 */
public class FarmerExtraHarvestBehavior extends AbstractVillagerWorkBehavior {

    private final List<BlockPos> targets = new ArrayList<>();
    private @Nullable BlockPos current;
    private int harvested;

    public FarmerExtraHarvestBehavior() {
        super(VillagerProfession.FARMER, Activity.WORK);
    }

    @Override
    protected int retryDelay() {
        return VillagerWorkConstants.FARMER_SCAN_INTERVAL_TICKS;
    }

    @Override
    protected boolean plan(ServerLevel level, Villager body, BlockPos jobSite, long now) {
        if (!net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, body)) {
            return false;
        }
        if (VillagerWork.freeSlots(body.getInventory()) < 1 || VillagerWork.chest(body, level).isEmpty()) {
            return false;
        }
        targets.clear();
        scan(level, jobSite);
        if (targets.isEmpty()) {
            return false;
        }
        harvested = 0;
        current = null;
        return nextTarget(level, body, now);
    }

    private void scan(ServerLevel level, BlockPos jobSite) {
        int r = VillagerWorkConstants.RANGE;
        int v = VillagerWorkConstants.RANGE_VERTICAL;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                pos.set(jobSite.getX() + dx, jobSite.getY(), jobSite.getZ() + dz);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                for (int dy = -v; dy <= v; dy++) {
                    pos.setY(jobSite.getY() + dy);
                    if (isTarget(level, pos)) {
                        targets.add(pos.immutable());
                    }
                }
            }
        }
    }

    /** A stem-grown pumpkin/melon, or the 2nd block of a sugar cane column. */
    private static boolean isTarget(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
            return stemFor(level, pos) != null;
        }
        if (state.is(Blocks.SUGAR_CANE)) {
            return level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)
                    && !level.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE);
        }
        return false;
    }

    private static @Nullable BlockPos stemFor(ServerLevel level, BlockPos fruit) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos stemPos = fruit.relative(dir);
            if (!level.isLoaded(stemPos)) {
                continue;
            }
            BlockState stem = level.getBlockState(stemPos);
            if (stem.getBlock() instanceof AttachedStemBlock
                    && stem.getValue(AttachedStemBlock.FACING) == dir.getOpposite()) {
                return stemPos;
            }
        }
        return null;
    }

    private boolean nextTarget(ServerLevel level, Villager body, long now) {
        targets.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(body.position())));
        while (!targets.isEmpty()) {
            BlockPos next = targets.removeFirst();
            if (level.isLoaded(next) && isTarget(level, next)) {
                current = next;
                beginLeg(now);
                return true;
            }
        }
        current = null;
        return false;
    }

    @Override
    protected void work(ServerLevel level, Villager body, long now) {
        if (current == null || !level.isLoaded(current) || !isTarget(level, current)) {
            if (!nextTarget(level, body, now)) {
                finish();
            }
            return;
        }
        Vec3 centre = Vec3.atCenterOf(current);
        if (VillagerWork.withinReach(body, centre)) {
            harvest(level, body, current);
            harvested++;
            if (harvested >= VillagerWorkConstants.FARMER_MAX_HARVESTS_PER_RUN
                    || VillagerWork.freeSlots(body.getInventory()) < 1
                    || !nextTarget(level, body, now)) {
                finish();
            }
            return;
        }
        if (legTimedOut(now)) {
            // Unreachable: skip it and try the next one.
            if (!nextTarget(level, body, now)) {
                fail(body, now);
            }
            return;
        }
        BlockState state = level.getBlockState(current);
        BlockPos walk = current;
        if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
            BlockPos stem = stemFor(level, current);
            walk = stem != null ? stem : current; // stand on the stem, next to the fruit
        } else {
            walk = current.below(); // bottom cane
        }
        walkTo(body, walk, 1);
    }

    private static void harvest(ServerLevel level, Villager body, BlockPos target) {
        body.swing(InteractionHand.MAIN_HAND);
        body.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        BlockState state = level.getBlockState(target);
        if (state.is(Blocks.SUGAR_CANE)) {
            BlockPos top = target;
            while (level.getBlockState(top.above()).is(Blocks.SUGAR_CANE)) {
                top = top.above();
            }
            // Top-down, so no cane pops off as a loose item.
            for (BlockPos p = top; p.getY() >= target.getY(); p = p.below()) {
                breakInto(level, body, p);
            }
        } else {
            breakInto(level, body, target);
        }
    }

    private static void breakInto(ServerLevel level, Villager body, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, body, ItemStack.EMPTY);
        if (level.destroyBlock(pos, false, body)) {
            for (ItemStack drop : drops) {
                VillagerWork.giveOrDrop(body, level, drop);
            }
        }
    }
}
