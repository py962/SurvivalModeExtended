package com.terrainlab.event;

import com.terrainlab.registry.ModAttachments;
import com.terrainlab.village.VillageConstants;
import com.terrainlab.village.VillageMembershipService;
import com.terrainlab.village.VillageReputationService;
import com.terrainlab.village.VillagerFearService;
import com.terrainlab.village.VillagerMoodService;
import com.terrainlab.village.VillagerScheduleService;
import com.terrainlab.village.VillagerThermalService;
import com.terrainlab.village.VillagerTiredService;
import com.terrainlab.village.VillagerTrappedService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Staggered per-villager behaviour reads (thermal, tired, trapped, schedule, hostile and
 * Sentry-trim sightings, mood), the damage fear hook, and the Fearful-at-dusk override that
 * sends villagers to the nearest bed.
 * <p>
 * The bed override uses a raw {@code navigation.moveTo}, which other AI may overwrite; it is
 * re-issued on every staggered read while the condition holds.
 */
public final class VillagerBehaviorHandler {

    private static final int BED_SEARCH_RADIUS = 48; // blocks
    private static final double WALK_SPEED = 0.6D;

    private VillagerBehaviorHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount(); // staggering only

        for (ServerLevel level : event.getServer().getAllLevels()) {
            long gameTime = level.getGameTime(); // timestamps, consistent with the damage hook
            // Snapshot the due villagers first: the services below can add/remove entities or
            // load chunks, which would rehash the live entity map mid-iteration.
            List<Villager> due = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Villager v && isStaggeredThisTick(v, tick)) {
                    due.add(v);
                }
            }
            for (Villager villager : due) {
                if (villager.isRemoved()) {
                    continue;
                }
                VillagerThermalService.update(villager);
                VillagerTiredService.update(villager, gameTime);
                VillagerTrappedService.update(villager, level);
                VillagerScheduleService.update(villager, level);
                checkForNearbyHostiles(villager, level, gameTime);
                applyFearfulDuskOverride(villager, level, gameTime);
                checkForSentryTrimSighting(villager, level, gameTime);
                // Last, so it sees this read's thermal/tired/trapped/fear results.
                VillagerMoodService.update(villager, gameTime);
            }
        }
    }

    private static boolean isStaggeredThisTick(Villager villager, long gameTime) {
        return Math.floorMod(villager.getId() + gameTime, VillageConstants.STAGGER_INTERVAL_TICKS) == 0;
    }

    @SubscribeEvent
    public static void onVillagerDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Villager villager) {
            VillagerFearService.onPossiblePanicTrigger(villager, villager.level().getGameTime());
        }
    }

    /**
     * Fear trigger: any living Monster within FEAR_SIGHT_RADIUS and in line of sight.
     * Sleeping villagers see nothing.
     */
    private static void checkForNearbyHostiles(Villager villager, ServerLevel level, long gameTime) {
        if (villager.isSleeping()) {
            return;
        }

        double radius = VillageConstants.FEAR_SIGHT_RADIUS;
        AABB searchBox = villager.getBoundingBox().inflate(radius);
        for (Monster monster : level.getEntitiesOfClass(Monster.class, searchBox, m -> m.isAlive())) {
            if (villager.hasLineOfSight(monster)) {
                VillagerFearService.onPossiblePanicTrigger(villager, gameTime);
                return;
            }
        }
    }

    /**
     * Reputation penalty when a villager sees a player wearing the Sentry trim, applied to
     * the sighting villager's own village (none if it has no known village). A per-player
     * global cooldown stops repeated penalties from every villager in view.
     */
    private static void checkForSentryTrimSighting(Villager villager, ServerLevel level, long gameTime) {
        if (villager.isSleeping()) {
            return;
        }

        double radius = VillageConstants.SENTRY_TRIM_SIGHT_RADIUS;
        AABB searchBox = villager.getBoundingBox().inflate(radius);
        for (Player player : level.getEntitiesOfClass(Player.class, searchBox, Player::isAlive)) {
            if (!SentryTrimIllagerDefenseHandler.isWearingSentryTrim(player)) {
                continue;
            }
            if (!villager.hasLineOfSight(player)) {
                continue;
            }

            long lastPenalty = player.getData(ModAttachments.PLAYER_LAST_SENTRY_TRIM_PENALTY_TICK.get());
            if (lastPenalty >= 0 && gameTime - lastPenalty < VillageConstants.SENTRY_TRIM_REPUTATION_COOLDOWN_TICKS) {
                continue; // still on cooldown
            }

            Optional<BlockPos> bell = VillageMembershipService.findMemberBell(level, villager.blockPosition());
            if (bell.isEmpty()) {
                continue; // villager has no known village
            }

            VillageReputationService.applyToPlayer(level, bell.get(), player.getUUID(),
                    VillageConstants.SENTRY_TRIM_REPUTATION_PENALTY);
            player.setData(ModAttachments.PLAYER_LAST_SENTRY_TRIM_PENALTY_TICK.get(), gameTime);
            return;
        }
    }

    /**
     * Fearful villagers at dusk head straight for the nearest bed within
     * {@link #BED_SEARCH_RADIUS}; with no bed in range they are left alone.
     */
    private static void applyFearfulDuskOverride(Villager villager, ServerLevel level, long gameTime) {
        if (!isDusk(level) || !VillagerFearService.isFearful(villager, gameTime)) {
            return;
        }

        PoiManager poiManager = level.getPoiManager();
        Optional<BlockPos> bedPos = poiManager.findClosest(
                holder -> holder.is(PoiTypes.HOME),
                villager.blockPosition(),
                BED_SEARCH_RADIUS,
                PoiManager.Occupancy.ANY);

        bedPos.ifPresent(pos -> villager.getNavigation().moveTo(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, WALK_SPEED));
    }

    private static boolean isDusk(Level level) {
        return com.terrainlab.season.SolarTime.isSolarDusk(level, VillageConstants.DUSK_LENGTH_TICKS);
    }
}
