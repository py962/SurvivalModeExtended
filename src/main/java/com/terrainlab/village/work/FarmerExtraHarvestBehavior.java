package com.terrainlab.village.work;

import com.terrainlab.village.VillagerWorkConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Farmer: harvests crops across the whole work area around the job site, not just the patch vanilla's
 * HarvestFarmland reaches. Mature wheat, carrots, potatoes and beetroot (replanted at once from the
 * drops, so the field never goes bare); mature pumpkins and melons (the fruit block next to an attached stem; the stem stays) and sugar cane
 * (everything from the 2nd block of a column up; the bottom block stays to regrow). Drops go straight
 * into the farmer's inventory (overflow is dropped). Needs mobGriefing, a village chest (the produce
 * is for it) and a free inventory slot. Up to {@link VillagerWorkConstants#FARMER_MAX_HARVESTS_PER_RUN}
 * blocks per run, nearest first.
 */
public class FarmerExtraHarvestBehavior extends AbstractVillagerWorkBehavior {

    /** Farmland crops harvested at full age, and the item that replants each. */
    private static final Map<Block, Item> CROP_SEEDS = Map.of(
            Blocks.WHEAT, Items.WHEAT_SEEDS,
            Blocks.CARROTS, Items.CARROT,
            Blocks.POTATOES, Items.POTATO,
            Blocks.BEETROOTS, Items.BEETROOT_SEEDS);

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

    /** A fully grown farmland crop, a stem-grown pumpkin/melon, or the 2nd block of a sugar cane column. */
    private static boolean isTarget(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (CROP_SEEDS.containsKey(state.getBlock())) {
            return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
        }
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
        if (CROP_SEEDS.containsKey(state.getBlock())) {
            walk = current; // beside or on the crop
        } else if (state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON)) {
            BlockPos stem = stemFor(level, current);
            walk = stem != null ? stem : current; // stand on the stem, next to the fruit
        } else {
            walk = current.below(); // bottom cane
        }
        walkTo(body, walk, 1);
    }

    private static void harvest(ServerLevel level, Villager body, BlockPos target) {
        body.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
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
        } else if (CROP_SEEDS.containsKey(state.getBlock())) {
            breakInto(level, body, target);
            // Replant from the inventory (the drops just went in).
            if (takeOne(body, CROP_SEEDS.get(state.getBlock()))) {
                level.setBlock(target, state.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            }
        } else {
            breakInto(level, body, target);
        }
    }

    private static boolean takeOne(Villager body, Item item) {
        var inventory = body.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
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
