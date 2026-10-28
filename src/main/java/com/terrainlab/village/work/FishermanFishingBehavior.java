package com.terrainlab.village.work;

import com.terrainlab.village.VillagerWorkConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Fisherman: finds open water in the work area (a 3×3 patch of water source blocks open to the sky),
 * walks to a standable block on its edge, faces the water holding a fishing rod (display only, see
 * {@link FishermanRod}; held only in the FISH phase and put away whenever the run stops) and fishes:
 * after a uniform {@link VillagerWorkConstants#FISH_MIN_TICKS}–
 * {@link VillagerWorkConstants#FISH_MAX_TICKS} wait, a splash at the bobber point and one catch into
 * its inventory (cod / salmon / dried kelp; salmon likelier in cold biomes; never tropical fish).
 * Recasts until it carries {@link VillagerWorkConstants#FISH_DELIVER_AT} items (then
 * {@link DepositProduceBehavior} delivers, fish cooked), or up to
 * {@link VillagerWorkConstants#FISH_MAX_WITHOUT_CHEST} when the village has no chest. No bobber entity:
 * vanilla's FishingHook needs a Player owner.
 */
public class FishermanFishingBehavior extends AbstractVillagerWorkBehavior {

    private enum Phase { WALK, FISH }

    private Phase phase = Phase.WALK;
    private @Nullable BlockPos stand;
    private @Nullable Vec3 bobber;
    private long biteAt;
    private long nextScan;
    private float savedHandDropChance = net.minecraft.world.entity.DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE;

    public FishermanFishingBehavior() {
        super(VillagerProfession.FISHERMAN, Activity.WORK);
    }

    private int limit(ServerLevel level, Villager body) {
        return VillagerWork.chest(body, level).isPresent()
                ? VillagerWorkConstants.FISH_DELIVER_AT
                : VillagerWorkConstants.FISH_MAX_WITHOUT_CHEST;
    }

    @Override
    protected boolean plan(ServerLevel level, Villager body, BlockPos jobSite, long now) {
        if (VillagerProducePlans.catchCount(body) >= limit(level, body)
                || VillagerWork.freeSlots(body.getInventory()) < 1) {
            return false;
        }
        boolean cachedOk = stand != null && bobber != null
                && VillagerWork.inWorkArea(jobSite, stand)
                && level.isLoaded(stand) && isStandable(level, stand)
                && isOpenWater(level, BlockPos.containing(bobber));
        if (!cachedOk) {
            stand = null;
            bobber = null;
            if (now < nextScan) {
                return false;
            }
            nextScan = now + VillagerWorkConstants.FISH_WATER_SCAN_INTERVAL_TICKS;
            if (!findSpot(level, body, jobSite)) {
                return false;
            }
        }
        phase = Phase.WALK;
        beginLeg(now);
        return true;
    }

    // ---------------------------------------------------------------- water search

    /** Water source with non-colliding, non-water space above and sky access. */
    private static boolean isSurfaceWater(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
            return false;
        }
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        return aboveState.getFluidState().isEmpty() && aboveState.getCollisionShape(level, above).isEmpty();
    }

    /** Centre of a 3×3 surface-water patch under open sky. */
    private static boolean isOpenWater(ServerLevel level, BlockPos centre) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!isSurfaceWater(level, centre.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return level.canSeeSky(centre.above());
    }

    /** Solid ground below, two free non-fluid blocks for the body. */
    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.isLoaded(below) || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return false;
        }
        for (BlockPos p : new BlockPos[]{pos, pos.above()}) {
            BlockState s = level.getBlockState(p);
            if (!s.getFluidState().isEmpty() || !s.getCollisionShape(level, p).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean findSpot(ServerLevel level, Villager body, BlockPos jobSite) {
        int r = VillagerWorkConstants.RANGE;
        int v = VillagerWorkConstants.RANGE_VERTICAL;
        BlockPos bestStand = null;
        BlockPos bestWater = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Every 2nd column is enough to hit any 3×3 patch; the patch is then centred on the hit.
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                pos.set(jobSite.getX() + dx, jobSite.getY(), jobSite.getZ() + dz);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                for (int dy = v; dy >= -v; dy--) {
                    pos.setY(jobSite.getY() + dy);
                    if (!isSurfaceWater(level, pos)) {
                        continue;
                    }
                    BlockPos centre = centreOfPatch(level, pos.immutable());
                    if (centre == null) {
                        break; // surface of this column found, not open water
                    }
                    BlockPos edge = standOnEdge(level, centre, jobSite);
                    if (edge != null) {
                        double d = edge.distSqr(body.blockPosition());
                        if (d < bestDist) {
                            bestDist = d;
                            bestStand = edge;
                            bestWater = centre;
                        }
                    }
                    break;
                }
            }
        }
        if (bestStand == null) {
            return false;
        }
        stand = bestStand;
        bobber = Vec3.atCenterOf(bestWater).add(0.0D, 0.4D, 0.0D);
        return true;
    }

    private static @Nullable BlockPos centreOfPatch(ServerLevel level, BlockPos hit) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos c = hit.offset(dx, 0, dz);
                if (isOpenWater(level, c)) {
                    return c;
                }
            }
        }
        return null;
    }

    /** A standable block 2–3 blocks from the patch centre (bank beside the patch), at water level to +2. */
    private static @Nullable BlockPos standOnEdge(ServerLevel level, BlockPos centre, BlockPos jobSite) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring < 2) {
                    continue;
                }
                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos p = centre.offset(dx, dy, dz);
                    if (!VillagerWork.inWorkArea(jobSite, p) || !level.isLoaded(p)) {
                        continue;
                    }
                    if (isStandable(level, p)) {
                        double d = p.distSqr(centre);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                        break;
                    }
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- work

    @Override
    protected void work(ServerLevel level, Villager body, long now) {
        if (stand == null || bobber == null) {
            fail(body, now);
            return;
        }
        Vec3 standCentre = Vec3.atBottomCenterOf(stand);
        switch (phase) {
            case WALK -> {
                if (body.position().distanceToSqr(standCentre) <= 1.5D * 1.5D) {
                    phase = Phase.FISH;
                    cast(level, body, now);
                } else if (legTimedOut(now)) {
                    stand = null; // forget an unreachable spot
                    bobber = null;
                    fail(body, now);
                } else {
                    walkTo(body, stand, 0);
                }
            }
            case FISH -> {
                if (body.position().distanceToSqr(standCentre) > 3.0D * 3.0D) {
                    FishermanRod.putAway(body, savedHandDropChance);
                    phase = Phase.WALK; // pushed away: walk back, then recast
                    beginLeg(now);
                    return;
                }
                stayAndLook(body, bobber);
                holdRod(body);
                if (now % 40 == 0) {
                    level.sendParticles(ParticleTypes.FISHING, bobber.x, bobber.y, bobber.z, 2, 0.05D, 0.0D, 0.05D, 0.0D);
                }
                if (now >= biteAt) {
                    catchOne(level, body);
                    if (VillagerProducePlans.catchCount(body) >= limit(level, body)
                            || VillagerWork.freeSlots(body.getInventory()) < 1) {
                        finish();
                    } else {
                        cast(level, body, now);
                    }
                }
            }
        }
    }

    private void holdRod(Villager body) {
        savedHandDropChance = FishermanRod.hold(body, savedHandDropChance);
    }

    @Override
    protected void cleanup(ServerLevel level, Villager body, long now) {
        FishermanRod.putAway(body, savedHandDropChance);
    }

    private void cast(ServerLevel level, Villager body, long now) {
        holdRod(body);
        body.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.FISHING_BOBBER_THROW,
                SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        level.sendParticles(ParticleTypes.SPLASH, bobber.x, bobber.y, bobber.z, 4, 0.1D, 0.0D, 0.1D, 0.0D);
        int span = VillagerWorkConstants.FISH_MAX_TICKS - VillagerWorkConstants.FISH_MIN_TICKS;
        biteAt = now + VillagerWorkConstants.FISH_MIN_TICKS + level.getRandom().nextInt(span + 1);
    }

    private void catchOne(ServerLevel level, Villager body) {
        level.playSound(null, bobber.x, bobber.y, bobber.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL,
                0.25F, 1.0F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.4F);
        level.sendParticles(ParticleTypes.SPLASH, bobber.x, bobber.y, bobber.z, 12, 0.2D, 0.0D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.BUBBLE, bobber.x, bobber.y - 0.3D, bobber.z, 8, 0.2D, 0.1D, 0.2D, 0.0D);
        level.sendParticles(ParticleTypes.FISHING, bobber.x, bobber.y, bobber.z, 6, 0.2D, 0.0D, 0.2D, 0.0D);
        body.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL, 0.5F, 1.0F);
        VillagerWork.giveOrDrop(body, level, rollCatch(level, BlockPos.containing(bobber)));
    }

    private static ItemStack rollCatch(ServerLevel level, BlockPos water) {
        boolean cold = false;
        if (level.isLoaded(water)) {
            Holder<Biome> biome = level.getBiome(water);
            cold = biome.value().getBaseTemperature() < VillagerWorkConstants.FISH_COLD_TEMPERATURE;
        }
        int cod = cold ? VillagerWorkConstants.FISH_COLD_COD_WEIGHT : VillagerWorkConstants.FISH_COD_WEIGHT;
        int salmon = cold ? VillagerWorkConstants.FISH_COLD_SALMON_WEIGHT : VillagerWorkConstants.FISH_SALMON_WEIGHT;
        int roll = level.getRandom().nextInt(cod + salmon + VillagerWorkConstants.FISH_KELP_WEIGHT);
        if (roll < cod) {
            return new ItemStack(Items.COD);
        }
        if (roll < cod + salmon) {
            return new ItemStack(Items.SALMON);
        }
        return new ItemStack(Items.DRIED_KELP);
    }
}
