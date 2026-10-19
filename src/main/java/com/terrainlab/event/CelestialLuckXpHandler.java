
        package com.terrainlab.event;

import com.terrainlab.celestial.CelestialConduitRegistry;
import com.terrainlab.celestial.CelestialDebug;
import com.terrainlab.celestial.CelestialVariant;
import com.terrainlab.registry.ModAttachments;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CelestialLuckXpHandler {

    private CelestialLuckXpHandler() {
    }

    private static final int SHEAR_XP = 2;
    private static final int TAME_XP = 50;
    private static final int PLANT_XP = 2;
    private static final int TREE_XP = 10;
    private static final int ANIMAL_MATURE_XP = 10;

    private static final long PLANT_REWARD_COOLDOWN_TICKS = 24_000L;
    private static final int PLANT_REWARD_CLEANUP_INTERVAL_TICKS = 1_200;

    private static final int KILL_EXCLUSION_RADIUS = 3;
    private static final int KILL_EXCLUSION_TICKS = 2;

    private static final int TREE_CHECK_DELAY_TICKS = 1;

    private record DeadMobMark(
            ResourceKey<Level> dimension,
            BlockPos pos,
            long tick
    ) {
    }

    private record PlantSpot(
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
    }

    private record PendingTreeGrowth(
            ServerLevel level,
            BlockPos pos,
            BlockState originalState,
            long checkAfterTick
    ) {
    }

    private record PendingSheepShear(
            ServerLevel level,
            UUID sheepId,
            long checkAfterTick
    ) {
    }

    private static final List<DeadMobMark> recentDeaths =
            new ArrayList<>();

    private static final Map<PlantSpot, Long> plantRewards =
            new HashMap<>();

    private static final List<PendingTreeGrowth> pendingTreeGrowths =
            new ArrayList<>();

    private static final List<PendingSheepShear> pendingSheepShears =
            new ArrayList<>();

    private static final Set<UUID> trackedBabyAnimals =
            new HashSet<>();

    /*
     * Creates a collectible XP orb.
     * Reward orbs are marked so they cannot be doubled again.
     */
    private static void spawnRewardOrb(
            ServerLevel level,
            double x,
            double y,
            double z,
            int xp
    ) {
        if (xp <= 0) {
            return;
        }

        ExperienceOrb orb = new ExperienceOrb(level, x, y, z, xp);
        orb.setData(ModAttachments.LUCK_ORB_BOOSTED.get(), true);

        level.addFreshEntity(orb);
    }

    private static void spawnRewardOrb(
            ServerLevel level,
            BlockPos pos,
            int xp
    ) {
        spawnRewardOrb(
                level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                xp
        );
    }

    private static boolean hasLuckConduit(
            ServerLevel level,
            BlockPos pos
    ) {
        return CelestialConduitRegistry.get(level).hasActiveNearby(
                CelestialVariant.LUCK,
                level.dimension(),
                pos
        );
    }

    /*
     * Shared reward method used by the sheep, goat and caribou
     * shearing paths after they successfully drop wool/fur.
     * Shearing XP is awarded everywhere; no Luck Conduit is required.
     */
    public static void awardShearingXp(
            ServerLevel level,
            Entity animal
    ) {
        if (level == null || animal == null || animal.isRemoved()) {
            return;
        }

        spawnRewardOrb(
                level,
                animal.getX(),
                animal.getY() + animal.getBbHeight() * 0.5,
                animal.getZ(),
                SHEAR_XP
        );

        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log(
                    "LUCK: spawned {} XP for successful shearing of {}",
                    SHEAR_XP,
                    BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType())
            );
        }
    }

    /*
     * Record nearby mob deaths so their XP orbs are not doubled.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof ServerPlayer
                || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        recentDeaths.add(
                new DeadMobMark(
                        level.dimension(),
                        entity.blockPosition().immutable(),
                        level.getGameTime()
                )
        );
    }

    /*
     * Double eligible XP orbs inside an active Luck Conduit.
     * XP orbs associated with recent mob deaths are excluded.
     */
    @SubscribeEvent
    public static void onOrbJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || event.loadedFromDisk()
                || !(event.getEntity() instanceof ExperienceOrb orb)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (orb.getData(ModAttachments.LUCK_ORB_BOOSTED.get())) {
            return;
        }

        orb.setData(ModAttachments.LUCK_ORB_BOOSTED.get(), true);

        long now = level.getGameTime();

        recentDeaths.removeIf(
                mark -> now - mark.tick() > KILL_EXCLUSION_TICKS
        );

        BlockPos orbPos = orb.blockPosition();

        for (DeadMobMark mark : recentDeaths) {
            if (mark.dimension().equals(level.dimension())
                    && mark.pos().distSqr(orbPos)
                    <= (double) KILL_EXCLUSION_RADIUS
                    * KILL_EXCLUSION_RADIUS) {

                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "LUCK: XP orb (value {}) at {} treated as kill XP - not doubled",
                            orb.getValue(),
                            orbPos
                    );
                }

                return;
            }
        }

        if (orb.getValue() > 0 && hasLuckConduit(level, orbPos)) {
            ExperienceOrb bonus = new ExperienceOrb(
                    level,
                    orb.getX(),
                    orb.getY(),
                    orb.getZ(),
                    orb.getValue()
            );

            bonus.setData(
                    ModAttachments.LUCK_ORB_BOOSTED.get(),
                    true
            );

            level.addFreshEntity(bonus);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log(
                        "LUCK: doubled XP orb (value {}) at {}",
                        orb.getValue(),
                        orbPos
                );
            }
        }
    }

    /*
     * Sheep use vanilla shearing behaviour. Queue the interaction and
     * check on the following tick that shearing actually succeeded.
     *
     * Goats and caribou are handled by their existing custom handlers.
     */
    @SubscribeEvent
    public static void onShear(
            PlayerInteractEvent.EntityInteract event
    ) {
        if (!(event.getTarget() instanceof Sheep sheep)
                || !(event.getLevel() instanceof ServerLevel level)
                || !event.getItemStack().is(Items.SHEARS)
                || sheep.isSheared()) {
            // Shearing XP applies everywhere, like goats and caribou.
            return;
        }

        boolean alreadyQueued = pendingSheepShears.stream()
                .anyMatch(pending ->
                        pending.level() == level
                                && pending.sheepId().equals(sheep.getUUID())
                );

        if (alreadyQueued) {
            return;
        }

        pendingSheepShears.add(
                new PendingSheepShear(
                        level,
                        sheep.getUUID(),
                        level.getGameTime() + 1L
                )
        );
    }

    /*
     * Crop maturity: award 2 XP when a crop reaches maximum age
     * within an active Luck Conduit. Planting gives no reward.
     */
    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BlockState oldState = event.getOriginalState();
        BlockState newState = event.getState();

        IntegerProperty ageProperty = findAgeProperty(newState);

        if (ageProperty == null
                || !oldState.hasProperty(ageProperty)
                || oldState.getValue(ageProperty)
                .equals(newState.getValue(ageProperty))
                || newState.getValue(ageProperty)
                < maximumAge(ageProperty)) {
            return;
        }

        BlockPos pos = event.getPos().immutable();

        if (!hasLuckConduit(level, pos)) {
            return;
        }

        long now = level.getGameTime();

        PlantSpot spot = new PlantSpot(level.dimension(), pos);

        if (!claimPlantReward(spot, now)) {
            return;
        }

        spawnRewardOrb(level, pos, PLANT_XP);

        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log(
                    "LUCK: spawned {} XP for crop maturity at {}",
                    PLANT_XP,
                    pos
            );
        }
    }

    /*
     * Returns true (and records the reward) if this spot has not been
     * rewarded within the cooldown. Expired entries are purged
     * periodically from onServerTick rather than on every reward.
     */
    private static boolean claimPlantReward(PlantSpot spot, long now) {
        Long previous = plantRewards.get(spot);

        if (previous != null
                && now - previous <= PLANT_REWARD_COOLDOWN_TICKS) {
            return false;
        }

        plantRewards.put(spot, now);
        return true;
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        for (var property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty
                    && "age".equals(property.getName())) {
                return integerProperty;
            }
        }

        return null;
    }

    private static int maximumAge(IntegerProperty property) {
        return property.getPossibleValues()
                .stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }

    /*
     * Record tree-growth attempts for checking after the feature
     * has had an opportunity to generate.
     */
    @SubscribeEvent
    public static void onTreeGrowthAttempt(
            BlockGrowFeatureEvent event
    ) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.isCanceled()) {
            return;
        }

        BlockPos pos = event.getPos().immutable();
        BlockState state = level.getBlockState(pos);

        if (!state.is(BlockTags.SAPLINGS)
                && !state.is(Blocks.BAMBOO_SAPLING)
                && !state.is(Blocks.AZALEA)
                && !state.is(Blocks.FLOWERING_AZALEA)) {
            return;
        }

        pendingTreeGrowths.add(
                new PendingTreeGrowth(
                        level,
                        pos,
                        state,
                        level.getGameTime() + TREE_CHECK_DELAY_TICKS
                )
        );
    }

    /*
     * Processes pending tree growth and sheep shearing checks.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        if (!plantRewards.isEmpty()
                && server.getTickCount()
                % PLANT_REWARD_CLEANUP_INTERVAL_TICKS == 0) {
            plantRewards.entrySet().removeIf(entry -> {
                ServerLevel level = server.getLevel(
                        entry.getKey().dimension()
                );

                return level == null
                        || level.getGameTime() - entry.getValue()
                        > PLANT_REWARD_COOLDOWN_TICKS;
            });
        }

        Iterator<PendingSheepShear> sheepIterator =
                pendingSheepShears.iterator();

        while (sheepIterator.hasNext()) {
            PendingSheepShear pending = sheepIterator.next();
            ServerLevel level = pending.level();

            if (level.getServer() != server) {
                sheepIterator.remove();
                continue;
            }

            if (level.getGameTime() < pending.checkAfterTick()) {
                continue;
            }

            sheepIterator.remove();

            Entity entity = level.getEntity(pending.sheepId());

            if (!(entity instanceof Sheep sheep)
                    || sheep.isRemoved()
                    || !sheep.isSheared()) {
                continue;
            }

            awardShearingXp(level, sheep);
        }

        Iterator<PendingTreeGrowth> treeIterator =
                pendingTreeGrowths.iterator();

        while (treeIterator.hasNext()) {
            PendingTreeGrowth pending = treeIterator.next();
            ServerLevel level = pending.level();

            if (level.getServer() != server) {
                treeIterator.remove();
                continue;
            }

            if (level.getGameTime() < pending.checkAfterTick()) {
                continue;
            }

            treeIterator.remove();

            if (!hasLuckConduit(level, pending.pos())) {
                continue;
            }

            if (!isSuccessfulTreeGrowth(level, pending)) {
                continue;
            }

            long now = level.getGameTime();

            PlantSpot spot = new PlantSpot(
                    level.dimension(),
                    pending.pos()
            );

            if (!claimPlantReward(spot, now)) {
                continue;
            }

            spawnRewardOrb(level, pending.pos(), TREE_XP);

            if (CelestialDebug.VERBOSE) {
                CelestialDebug.log(
                        "LUCK: spawned {} XP for tree growth at {}",
                        TREE_XP,
                        pending.pos()
                );
            }
        }
    }

    private static boolean isSuccessfulTreeGrowth(
            ServerLevel level,
            PendingTreeGrowth pending
    ) {
        BlockPos origin = pending.pos();

        if (level.getBlockState(origin).equals(
                pending.originalState()
        )) {
            return false;
        }

        for (BlockPos check : BlockPos.betweenClosed(
                origin.offset(-3, 0, -3),
                origin.offset(3, 7, 3)
        )) {
            if (level.getBlockState(check).is(BlockTags.LOGS)) {
                return true;
            }
        }

        return false;
    }

    /*
     * Taming: 50 XP anywhere, or 100 XP inside an active Luck Conduit.
     */
    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (!(event.getTamer() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        int reward = hasLuckConduit(level, player.blockPosition())
                ? TAME_XP * 2
                : TAME_XP;

        spawnRewardOrb(
                level,
                player.getX(),
                player.getY() + 0.5,
                player.getZ(),
                reward
        );

        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log(
                    "LUCK: spawned {} XP for taming by {}",
                    reward,
                    player.getName().getString()
            );
        }
    }

    /*
     * Track baby animals when they enter the world. This also covers
     * bred offspring (added via addFreshEntity) and babies reloaded
     * from disk. Spawn-egg babies are not tracked.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof Animal animal
                && animal.isBaby()
                && animal.getSpawnType()
                != EntitySpawnReason.SPAWN_ITEM_USE) {
            trackedBabyAnimals.add(animal.getUUID());
        }
    }

    /*
     * Stop tracking babies that die, unload or change dimension.
     * They are re-tracked by onEntityJoin if they come back as babies.
     */
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof Animal)) {
            return;
        }

        trackedBabyAnimals.remove(event.getEntity().getUUID());
    }

    /*
     * Animal maturity: award 10 XP when a tracked baby becomes an adult
     * inside an active Luck Conduit. Birth alone gives no reward.
     */
    @SubscribeEvent
    public static void onAnimalTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Animal animal)
                || animal.level().isClientSide()
                || !trackedBabyAnimals.contains(animal.getUUID())) {
            return;
        }

        if (animal.isBaby()) {
            return;
        }

        trackedBabyAnimals.remove(animal.getUUID());

        if (!(animal.level() instanceof ServerLevel level)
                || !hasLuckConduit(level, animal.blockPosition())) {
            return;
        }

        spawnRewardOrb(
                level,
                animal.getX(),
                animal.getY() + animal.getBbHeight() * 0.5,
                animal.getZ(),
                ANIMAL_MATURE_XP
        );

        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log(
                    "LUCK: spawned {} XP for animal maturity ({})",
                    ANIMAL_MATURE_XP,
                    BuiltInRegistries.ENTITY_TYPE.getKey(
                            animal.getType()
                    )
            );
        }
    }
}
