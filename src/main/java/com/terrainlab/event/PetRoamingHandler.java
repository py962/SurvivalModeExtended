package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Optional;

/**
 * Keeps tamed Wolves and Cats within {@link #ROAM_RADIUS} of a roaming centre: their bed
 * anchor if set, otherwise the owner's position. Uses the vanilla Mob home, refreshed about
 * once a second (leashing overwrites it, so leashed pets are skipped).
 *
 * <p>Vanilla home only biases where strolls go; {@link MoveTowardsRestrictionGoal} is added
 * on join so pets far outside the zone walk back (goals are not saved, so this runs on every
 * join). There is deliberately no teleport catch-up.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class PetRoamingHandler {

    public static final int ROAM_RADIUS = 64;

    private static final int REFRESH_INTERVAL_TICKS = 20; // staggered per entity id

    /** Squared distance (4 blocks) the owner must move before an owner-centred zone is re-centred. */
    private static final double OWNER_RECENTRE_DIST_SQ = 16.0;

    private static final int WALK_BACK_GOAL_PRIORITY = 7;

    private static final boolean DEBUG_LOGGING = false;

    private static boolean isRoamer(Object entity) {
        return entity instanceof Wolf || entity instanceof Cat;
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!isRoamer(event.getEntity())) return;

        PathfinderMob mob = (PathfinderMob) event.getEntity();
        boolean alreadyHas = mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof MoveTowardsRestrictionGoal);
        if (alreadyHas) {
            // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] {} already has MoveTowardsRestrictionGoal", mob);
            return;
        }

        mob.goalSelector.addGoal(WALK_BACK_GOAL_PRIORITY, new MoveTowardsRestrictionGoal(mob, 1.0));
        // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] added MoveTowardsRestrictionGoal to {}", mob);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof TamableAnimal pet)) return;
        if (!isRoamer(pet)) return;
        if (pet.level().isClientSide()) return;
        if ((pet.tickCount + pet.getId()) % REFRESH_INTERVAL_TICKS != 0) return;

        refreshHome(pet);
    }

    private static void refreshHome(TamableAnimal pet) {
        if (!pet.isTame()) {
            // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] {} not tame - vanilla behaviour", pet);
            return;
        }
        if (pet.isOrderedToSit()) {
            // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] {} sitting - skip", pet);
            return;
        }
        if (pet.isPassenger()) {
            // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] {} riding - skip", pet);
            return;
        }
        if (pet.isLeashed()) {
            // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] {} leashed - leash owns the home, skip", pet);
            return;
        }

        Optional<BlockPos> anchor = pet.getData(ModAttachments.ANCHOR_BED_POS.get());
        boolean anchored = anchor.isPresent();

        BlockPos centre;
        if (anchored) {
            centre = anchor.get();
        } else {
            LivingEntity owner = pet.getOwner();
            if (owner == null) {
                // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] {} owner not loaded here - leaving home unchanged", pet);
                return;
            }
            centre = owner.blockPosition();
        }

        double tolerance = anchored ? 0.0 : OWNER_RECENTRE_DIST_SQ;
        boolean needsUpdate = !pet.hasHome()
                || pet.getHomeRadius() != ROAM_RADIUS
                || pet.getHomePosition().distSqr(centre) > tolerance;

        if (!needsUpdate) {
            // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] {} home already at {} ({}) - unchanged",
                    // pet, pet.getHomePosition(), anchored ? "bed" : "owner");
            return;
        }

        pet.setHomeTo(centre, ROAM_RADIUS);
        // if (DEBUG_LOGGING) TerrainLab.LOGGER.info("[PET_ROAM] {} home -> {} radius {} ({})",
                // pet, centre, ROAM_RADIUS, anchored ? "bed" : "owner");
    }
}
