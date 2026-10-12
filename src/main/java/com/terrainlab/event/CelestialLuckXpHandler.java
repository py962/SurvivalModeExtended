package com.terrainlab.event;

import com.terrainlab.celestial.CelestialConduitRegistry;
import com.terrainlab.celestial.CelestialDebug;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Luck conduit XP: doubles non-kill XP orbs (mining, smelting, trading) that appear within range
 * of an active Luck conduit, and grants fixed rewards for planting, taming and breeding there.
 * Kill XP is excluded by matching new orbs against recent mob deaths by position and time.
 */
public class CelestialLuckXpHandler {

    private static final int PLANT_XP = 2;
    private static final int TAME_XP = 20;
    private static final int BREED_XP = 10;
    private static final long PLANT_REWARD_COOLDOWN_TICKS = 24_000L; // one reward per spot per day

    private static final int KILL_EXCLUSION_RADIUS = 3;
    private static final int KILL_EXCLUSION_TICKS = 2;

    private record DeadMobMark(ResourceKey<Level> dimension, BlockPos pos, long tick) {
    }

    private static final List<DeadMobMark> recentDeaths = new ArrayList<>();

    /** Last planting reward per spot, so breaking and replanting cannot farm XP. */
    private record PlantSpot(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final java.util.Map<PlantSpot, Long> plantRewards = new java.util.HashMap<>();

    /** Records where and when a non-player entity died so its XP orbs are excluded from doubling. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof ServerPlayer || !(entity.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        recentDeaths.add(new DeadMobMark(serverLevel.dimension(), entity.blockPosition().immutable(), serverLevel.getGameTime()));
    }

    /**
     * Doubles a new XP orb by spawning an equal-value twin, unless it is kill XP or no active
     * Luck conduit is in range. {@code LUCK_ORB_BOOSTED} stops the twin from re-triggering.
     */
    @SubscribeEvent
    public static void onOrbJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()
                || !(event.getEntity() instanceof ExperienceOrb orb)) {
            return; // orbs reloaded from disk were already handled when they first appeared
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (orb.getData(ModAttachments.LUCK_ORB_BOOSTED.get())) {
            return; // twin orb or already handled
        }
        orb.setData(ModAttachments.LUCK_ORB_BOOSTED.get(), true);

        pruneOldDeaths(serverLevel.getGameTime());
        BlockPos orbPos = orb.blockPosition();
        for (DeadMobMark mark : recentDeaths) {
            if (mark.dimension() == serverLevel.dimension()
                    && mark.pos().distSqr(orbPos) <= (double) KILL_EXCLUSION_RADIUS * KILL_EXCLUSION_RADIUS) {
                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log("LUCK: XP orb (value {}) at {} treated as kill XP - not doubled", orb.getValue(), orbPos);
                }
                return; // kill XP
            }
        }

        if (orb.getValue() > 0 && CelestialConduitRegistry.get(serverLevel)
                .hasActiveNearby(CelestialVariant.LUCK, serverLevel.dimension(), orbPos)) {
            ExperienceOrb bonus = new ExperienceOrb(serverLevel, orb.getX(), orb.getY(), orb.getZ(), orb.getValue());
            bonus.setData(ModAttachments.LUCK_ORB_BOOSTED.get(), true);
            serverLevel.addFreshEntity(bonus);
            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log("LUCK: doubled XP orb (value {}) at {}", orb.getValue(), orbPos);
            }
        } else if (CelestialDebug.VERBOSE && orb.getValue() > 0) {
            CelestialDebug.log("LUCK: XP orb (value {}) at {} not doubled - no active Luck conduit in range", orb.getValue(), orbPos);
        }
    }

    private static void pruneOldDeaths(long now) {
        recentDeaths.removeIf(mark -> now - mark.tick() > KILL_EXCLUSION_TICKS);
    }

    /** Planting reward within an active Luck conduit's range. */
    @SubscribeEvent
    public static void onPlant(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        if (!isPlantable(event.getPlacedBlock())) return;

        if (CelestialConduitRegistry.get(serverLevel)
                .hasActiveNearby(CelestialVariant.LUCK, serverLevel.dimension(), event.getPos())) {
            long now = serverLevel.getGameTime();
            plantRewards.values().removeIf(tick -> now - tick > PLANT_REWARD_COOLDOWN_TICKS);
            PlantSpot spot = new PlantSpot(serverLevel.dimension(), event.getPos().immutable());
            if (plantRewards.putIfAbsent(spot, now) != null) return;
            player.giveExperiencePoints(PLANT_XP);
            if (CelestialDebug.VERBOSE) CelestialDebug.log("LUCK: +{} XP to {} for planting at {}", PLANT_XP, player.getName().getString(), event.getPos());
        } else if (CelestialDebug.VERBOSE) {
            CelestialDebug.log("LUCK: planting at {} by {} - no active Luck conduit in range", event.getPos(), player.getName().getString());
        }
    }

    private static boolean isPlantable(BlockState state) {
        return state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.NETHER_WART)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.BAMBOO_SAPLING)
                || state.getBlock() instanceof StemBlock;
    }

    /** Taming reward within an active Luck conduit's range. */
    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (!(event.getTamer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        if (CelestialConduitRegistry.get(serverLevel)
                .hasActiveNearby(CelestialVariant.LUCK, serverLevel.dimension(), player.blockPosition())) {
            player.giveExperiencePoints(TAME_XP);
            if (CelestialDebug.VERBOSE) CelestialDebug.log("LUCK: +{} XP to {} for taming", TAME_XP, player.getName().getString());
        } else if (CelestialDebug.VERBOSE) {
            CelestialDebug.log("LUCK: taming by {} at {} - no active Luck conduit in range", player.getName().getString(), player.blockPosition());
        }
    }

    /** Breeding reward within an active Luck conduit's range. */
    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        if (CelestialConduitRegistry.get(serverLevel)
                .hasActiveNearby(CelestialVariant.LUCK, serverLevel.dimension(), player.blockPosition())) {
            player.giveExperiencePoints(BREED_XP);
            if (CelestialDebug.VERBOSE) CelestialDebug.log("LUCK: +{} XP to {} for breeding", BREED_XP, player.getName().getString());
        } else if (CelestialDebug.VERBOSE) {
            CelestialDebug.log("LUCK: breeding by {} at {} - no active Luck conduit in range", player.getName().getString(), player.blockPosition());
        }
    }
}
