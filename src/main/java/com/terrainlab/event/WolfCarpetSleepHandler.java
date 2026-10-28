package com.terrainlab.event;

import com.terrainlab.TerrainLab;
import com.terrainlab.pet.WolfCarpetSleep;
import com.terrainlab.pet.WolfCarpetSleepGoals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * When a player lies down in any bed that uses vanilla sleeping (detected as the not-sleeping to
 * sleeping edge, so the mod's houseboat/howdah beds count too), their tamed wolves within
 * {@link #RADIUS} of the bed that are not sitting, leashed or riding each claim a free carpet within
 * {@link #RADIUS} of the bed (nearest wolf-carpet pairs first, one wolf per carpet, carpet must have
 * air above, not be claimed by another wolf and be reachable), walk there and lie down
 * ({@link WolfCarpetSleepGoals}). Wolves left without a carpet carry on as normal.
 *
 * <p>A wolf wakes (claim cleared, normal AI resumes) when its owner is no longer sleeping (woke, left
 * the bed, logged out, changed dimension), when it takes damage, gets a target or a hostile mob comes
 * within {@link #HOSTILE_RADIUS}, when its carpet is removed, when it is moved off the carpet
 * (teleports), or when it is ordered to sit, leashed or mounted. The state is transient, so a reloaded
 * wolf is always awake. Bed anchoring and the roaming zone are untouched.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public class WolfCarpetSleepHandler {

    private static final int RADIUS = 16;
    private static final double HOSTILE_RADIUS = 8.0;
    private static final int HOSTILE_CHECK_INTERVAL = 10;
    private static final double OFF_CARPET_DIST_SQ = 1.5 * 1.5;
    private static final int MAX_PATH_CHECKS = 64;
    private static final int PATH_LENGTH = 48;

    /** Players seen sleeping last tick (server), for the start-sleeping edge. */
    private static final Set<UUID> SLEEPING = new HashSet<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Wolf wolf)) return;
        boolean has = wolf.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof WolfCarpetSleepGoals.Sleep);
        if (has) return;
        wolf.goalSelector.addGoal(WolfCarpetSleepGoals.SLEEP_PRIORITY, new WolfCarpetSleepGoals.Sleep(wolf));
        wolf.goalSelector.addGoal(WolfCarpetSleepGoals.WALK_PRIORITY, new WolfCarpetSleepGoals.WalkToCarpet(wolf));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!player.isSleeping()) {
            SLEEPING.remove(player.getUUID());
            return;
        }
        if (!SLEEPING.add(player.getUUID())) return;
        player.getSleepingPos().ifPresent(bed -> sendWolvesToCarpets(level, player, bed));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SLEEPING.remove(event.getEntity().getUUID());
    }

    private record Pair(Wolf wolf, BlockPos carpet, double distSq) {}

    private static void sendWolvesToCarpets(ServerLevel level, Player player, BlockPos bed) {
        Vec3 bedCentre = Vec3.atCenterOf(bed);
        double radiusSq = (double) RADIUS * RADIUS;
        AABB area = new AABB(bed).inflate(RADIUS);

        List<Wolf> wolves = level.getEntitiesOfClass(Wolf.class, area, w -> w.isAlive()
                && w.isTame()
                && w.isOwnedBy(player)
                && !w.isOrderedToSit()
                && !w.isLeashed()
                && !w.isPassenger()
                && w.getTarget() == null
                && !WolfCarpetSleep.hasCarpet(w)
                && w.distanceToSqr(bedCentre) <= radiusSq);
        if (wolves.isEmpty()) return;

        // Carpets already claimed by other wolves (another owner's, or from an earlier sleep).
        Set<BlockPos> claimed = new HashSet<>();
        for (Wolf other : level.getEntitiesOfClass(Wolf.class, area.inflate(RADIUS), WolfCarpetSleep::hasCarpet)) {
            WolfCarpetSleep.carpet(other).ifPresent(claimed::add);
        }

        List<BlockPos> carpets = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(bed.offset(-RADIUS, -RADIUS, -RADIUS), bed.offset(RADIUS, RADIUS, RADIUS))) {
            if (pos.distSqr(bed) > radiusSq) continue;
            if (!level.isLoaded(pos)) continue;
            if (!WolfCarpetSleep.isFreeCarpet(level, pos)) continue;
            BlockPos immutable = pos.immutable();
            if (!claimed.contains(immutable)) carpets.add(immutable);
        }
        if (carpets.isEmpty()) return;

        List<Pair> pairs = new ArrayList<>();
        for (Wolf wolf : wolves) {
            for (BlockPos carpet : carpets) {
                pairs.add(new Pair(wolf, carpet, wolf.distanceToSqr(Vec3.atBottomCenterOf(carpet))));
            }
        }
        pairs.sort(Comparator.comparingDouble(Pair::distSq));

        Set<Wolf> doneWolves = new HashSet<>();
        Set<BlockPos> usedCarpets = new HashSet<>();
        int pathChecks = 0;
        for (Pair pair : pairs) {
            if (doneWolves.size() == wolves.size() || usedCarpets.size() == carpets.size()) break;
            if (doneWolves.contains(pair.wolf) || usedCarpets.contains(pair.carpet)) continue;
            if (pathChecks++ >= MAX_PATH_CHECKS) break;
            if (!isReachable(pair.wolf, pair.carpet)) continue;
            WolfCarpetSleep.assign(pair.wolf, pair.carpet);
            doneWolves.add(pair.wolf);
            usedCarpets.add(pair.carpet);
        }
    }

    private static boolean isReachable(Wolf wolf, BlockPos carpet) {
        if (wolf.blockPosition().equals(carpet)) return true;
        Path path = wolf.getNavigation().createPath(carpet, 0, PATH_LENGTH);
        return path != null && path.canReach();
    }

    @SubscribeEvent
    public static void onWolfTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Wolf wolf)) return;
        if (wolf.level().isClientSide()) return;
        Optional<BlockPos> claimed = WolfCarpetSleep.carpet(wolf);
        if (claimed.isEmpty()) {
            if (WolfCarpetSleep.isAsleep(wolf)) WolfCarpetSleep.wake(wolf); // stray flag
            return;
        }
        if (shouldWake(wolf, claimed.get())) {
            WolfCarpetSleep.wake(wolf);
        }
    }

    private static boolean shouldWake(Wolf wolf, BlockPos carpet) {
        if (!wolf.isAlive() || !wolf.isTame()) return true;
        LivingEntity owner = wolf.getOwner();
        if (!(owner instanceof Player player) || !player.isSleeping()) return true;
        if (wolf.isOrderedToSit() || wolf.isLeashed() || wolf.isPassenger()) return true;
        if (wolf.getTarget() != null) return true;

        boolean asleep = WolfCarpetSleep.isAsleep(wolf);
        if (asleep) {
            if (!WolfCarpetSleep.isCarpet(wolf.level().getBlockState(carpet))) return true;
            if (WolfCarpetSleepGoals.horizontalDistSq(wolf, carpet) > OFF_CARPET_DIST_SQ
                    || Math.abs(wolf.getY() - carpet.getY()) > 1.5) return true;
        } else if (!WolfCarpetSleep.isFreeCarpet(wolf.level(), carpet)) {
            return true; // removed or blocked before it got there
        }

        if ((wolf.tickCount + wolf.getId()) % HOSTILE_CHECK_INTERVAL == 0) {
            double r2 = HOSTILE_RADIUS * HOSTILE_RADIUS;
            return !wolf.level().getEntitiesOfClass(Mob.class, wolf.getBoundingBox().inflate(HOSTILE_RADIUS),
                    m -> m instanceof Enemy && m.isAlive() && m.distanceToSqr(wolf) <= r2).isEmpty();
        }
        return false;
    }

    @SubscribeEvent
    public static void onWolfDamaged(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Wolf wolf)) return;
        if (wolf.level().isClientSide()) return;
        if (WolfCarpetSleep.hasCarpet(wolf) || WolfCarpetSleep.isAsleep(wolf)) {
            WolfCarpetSleep.wake(wolf);
        }
    }
}
