
package com.terrainlab.celestial;

import com.terrainlab.energy.PlayerEnergyConstants;
import com.terrainlab.event.CelestialEventHandler;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModMobEffects;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.animal.Animal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CelestialEffects {

    private static final RandomSource RANDOM = RandomSource.create();

    // Conduit checks happen every 40 ticks.
    private static final int EFFECT_DURATION_TICKS = 260;

    /*
     * Shield environmental scan.
     *
     * The scan advances in bounded batches and never force-loads chunks.
     */
    private static final int CLIMATE_SCAN_INTERVAL_TICKS = 40;
    private static final int CLIMATE_SCAN_BLOCK_BUDGET = 40_000;

    /*
     * Life: plants in range grow at LIFE_GROWTH_MULTIPLIER x vanilla. A bounded incremental scan
     * finds crops, saplings, berry bushes, cocoa and nether wart throughout the sphere; every
     * recorded plant then gets extra random ticks each conduit check, so it still needs light,
     * water and farmland like any crop, and the speed is the same at any radius.
     */
    private static final int LIFE_GROWTH_MULTIPLIER = 5;
    // Plus the original effect: random bonemeal on anything bonemealable (grass, flowers, ...).
    private static final int LIFE_BONEMEAL_ATTEMPTS = 64;
    private static final int LIFE_SCAN_BLOCK_BUDGET = 40_000;
    // Vanilla gives each block randomTickSpeed (3) / 4096 random ticks per game tick.
    private static final double VANILLA_RANDOM_TICKS_PER_TICK = 3.0 / 4096.0;
    private static final long LIFE_MAX_ELAPSED_TICKS = 200L;

    private static final Map<ClimateScanKey, GrowthScanState> GROWTH_SCANS =
            new HashMap<>();

    private static final Map<ClimateScanKey, ClimateScanState> CLIMATE_SCANS =
            new HashMap<>();

    private CelestialEffects() {
    }

    public static void tickConduit(
            ServerLevel level,
            BlockPos pos,
            CelestialVariant variant,
            int radius,
            boolean fullPower
    ) {
        if (radius <= 0) {
            return;
        }

        BlockPos center = pos.immutable();
        double centerX = center.getX() + 0.5D;
        double centerY = center.getY() + 0.5D;
        double centerZ = center.getZ() + 0.5D;
        double radiusSquared = (double) radius * radius;

        AABB area = new AABB(center).inflate(radius);

        // AABB is only a broad-phase query. The actual conduit range is spherical.
        List<ServerPlayer> players = level.getEntitiesOfClass(
                ServerPlayer.class,
                area,
                player -> player.distanceToSqr(centerX, centerY, centerZ)
                        <= radiusSquared
        );

        // BREATH uses vanilla's conduit and has no CONDUIT_MARKERS entry.
        if (variant.usesVanillaConduit()) {
            return;
        }

        for (ServerPlayer player : players) {
            give(player, ModMobEffects.CONDUIT_MARKERS.get(variant));
        }

        if (CelestialDebug.VERBOSE) {
            CelestialDebug.log(
                    "tickConduit {} at {} radius={} fullPower={}: {} player(s) in range {}",
                    variant,
                    center,
                    radius,
                    fullPower,
                    players.size(),
                    players.stream().map(p -> p.getName().getString()).toList()
            );
        }

        switch (variant) {
            case BREATH -> {
                // Handled by vanilla ConduitBlockEntity.
            }

            case LIFE -> {
                for (ServerPlayer player : players) {
                    give(player, MobEffects.REGENERATION, 0);
                    boostEnergy(player, 0.5F);
                }

                int healedMobs = 0;

                for (Mob mob : level.getEntitiesOfClass(
                        Mob.class,
                        area,
                        entity -> entity.isAlive()
                                && !(entity instanceof Enemy)
                                && entity.distanceToSqr(centerX, centerY, centerZ)
                                <= radiusSquared
                )) {
                    give(mob, MobEffects.REGENERATION, 0);
                    healedMobs++;
                }

                // Both effects use the full active conduit radius.
                accelerateGrowth(level, center, radius);
                randomBonemeal(level, center, radius);
                accelerateBabyGrowth(level, center, radius);
                accelerateBreedingCooldowns(level, center, radius);

                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "  LIFE: Regeneration + energy -> {} player(s); "
                                    + "Regeneration -> {} passive/neutral mob(s); "
                                    + "growth x{} radius={}",
                            players.size(),
                            healedMobs,
                            LIFE_GROWTH_MULTIPLIER,
                            radius
                    );
                }
            }

            case DEATH -> {
                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "  DEATH: no tick effects (respawn point / picking are event-driven)"
                    );
                }
            }

            case SHIELD -> {
                // Shield changes the local environment, not player internal temperature.
                localClimateControl(level, center, radius);

                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "  SHIELD: environmental climate scan running at radius={}",
                            radius
                    );
                }
            }

            case PEACE -> {
                // Clear existing hostile targets against protected NPCs as well as
                // preventing new targets through LivingChangeTargetEvent.
                CelestialEventHandler.clearPeaceNpcTargets(level, center, radius);

                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "  PEACE: hostile targeting suppression active at radius={}",
                            radius
                    );
                }
            }

            case EYE -> {
                for (ServerPlayer player : players) {
                    give(player, MobEffects.NIGHT_VISION);
                    give(player, MobEffects.INVISIBILITY);
                }

                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "  EYE: Night Vision + Invisibility -> {} player(s)",
                            players.size()
                    );
                }
            }

            case LUCK -> {
                for (ServerPlayer player : players) {
                    give(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
                    give(player, MobEffects.LUCK, 0);
                }

                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "  LUCK: Hero of the Village + Luck -> {} player(s)",
                            players.size()
                    );
                }
            }

            case END -> {
                if (CelestialDebug.VERBOSE) {
                    CelestialDebug.log(
                            "  END: fullPower={} dim={} - flight/float handled by CelestialEventHandler",
                            fullPower,
                            level.dimension().identifier()
                    );
                }
            }
        }
    }

    /** Applies the amulet effect for a wearer whose paired conduit is active. */
    public static void tickAmulet(ServerPlayer player, CelestialVariant variant) {
        switch (variant) {
            case BREATH -> give(player, MobEffects.WATER_BREATHING);
            case LIFE -> boostEnergy(player, 0.5F);
            case DEATH -> {
                // Event-driven: respawn-choice flow.
            }
            case SHIELD -> stabiliseTemperature(player, 20.0F, 20.0F);
            case PEACE -> {
                // Event-driven: LivingChangeTargetEvent hook.
            }
            case EYE -> give(player, MobEffects.NIGHT_VISION);
            case LUCK -> {
                // Same pair as the Luck conduit.
                give(player, MobEffects.HERO_OF_THE_VILLAGE, 0);
                give(player, MobEffects.LUCK, 0);
            }
            case END -> {
                // Float and levitation immunity are handled in CelestialEventHandler.
            }
        }
    }

    /** Keeps at most one amulet marker badge on the player. */
    public static void updateAmuletMarkers(
            ServerPlayer player,
            @javax.annotation.Nullable CelestialVariant activeAmulet
    ) {
        for (CelestialVariant variant : CelestialVariant.values()) {
            var marker = ModMobEffects.AMULET_MARKERS.get(variant);

            if (variant == activeAmulet) {
                give(player, marker);
            } else if (player.hasEffect(marker)) {
                player.removeEffect(marker);
            }
        }
    }

    /** Icon effect for benefiting from a conduit. */
    public static net.minecraft.core.Holder<MobEffect> conduitIconFor(
            CelestialVariant variant
    ) {
        return variant.usesVanillaConduit()
                ? MobEffects.CONDUIT_POWER
                : ModMobEffects.CONDUIT_MARKERS.get(variant);
    }

    private static void give(
            LivingEntity entity,
            net.minecraft.core.Holder<MobEffect> effect
    ) {
        give(entity, effect, 0);
    }

    private static void give(
            LivingEntity entity,
            net.minecraft.core.Holder<MobEffect> effect,
            int amplifier
    ) {
        entity.addEffect(new MobEffectInstance(
                effect,
                EFFECT_DURATION_TICKS,
                amplifier,
                true,
                false,
                true
        ));
    }

    private static void boostEnergy(ServerPlayer player, float amount) {
        float current = player.getData(ModAttachments.PLAYER_ENERGY.get());

        player.setData(
                ModAttachments.PLAYER_ENERGY.get(),
                Math.min(PlayerEnergyConstants.MAX_ENERGY, current + amount)
        );
    }

    private static void stabiliseTemperature(
            ServerPlayer player,
            float min,
            float max
    ) {
        float current = player.getData(ModAttachments.PLAYER_INTERNAL_TEMP.get());
        float target = Math.max(min, Math.min(max, current));
        float next = current + (target - current) * 0.2F;

        player.setData(ModAttachments.PLAYER_INTERNAL_TEMP.get(), next);
    }

    /** Plants the Life conduit speeds up. */
    private static boolean isLifeBoostedPlant(BlockState state) {
        return state.is(BlockTags.CROPS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.COCOA)
                || state.is(Blocks.NETHER_WART);
    }

    /**
     * Continues the incremental plant scan, then gives every recorded plant its share of extra
     * random ticks for the time since the last check. Unloaded chunks are skipped, never
     * force-loaded; plants that are gone are dropped from the record.
     */
    private static void accelerateGrowth(
            ServerLevel level,
            BlockPos center,
            int radius
    ) {
        if (radius <= 0) {
            return;
        }

        GrowthScanState scan = GROWTH_SCANS.computeIfAbsent(
                new ClimateScanKey(level.dimension(), center.immutable()),
                ignored -> new GrowthScanState(radius)
        );

        if (scan.radius != radius) {
            scan.radius = radius;
            scan.cursor = 0;
            scan.plants.clear();
        }

        // 1. Scan the next slice of the sphere for plants.
        int diameter = radius * 2 + 1;
        long totalPositions = (long) diameter * diameter * diameter;
        long radiusSquared = (long) radius * radius;
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        int processed = 0;

        while (processed < LIFE_SCAN_BLOCK_BUDGET && scan.cursor < totalPositions) {
            long index = scan.cursor++;
            processed++;

            int xIndex = (int) (index % diameter);
            int yIndex = (int) ((index / diameter) % diameter);
            int zIndex = (int) (index / ((long) diameter * diameter));

            long dx = xIndex - radius;
            long dy = yIndex - radius;
            long dz = zIndex - radius;

            if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                continue;
            }

            int y = center.getY() + (int) dy;

            if (y < level.getMinY() || y >= level.getMaxY()) {
                continue;
            }

            check.set(center.getX() + (int) dx, y, center.getZ() + (int) dz);

            if (level.hasChunkAt(check) && isLifeBoostedPlant(level.getBlockState(check))) {
                scan.plants.add(check.asLong());
            }
        }

        if (scan.cursor >= totalPositions) {
            scan.cursor = 0;
        }

        // 2. Extra random ticks: (multiplier - 1) x vanilla's rate over the elapsed time.
        long gameTime = level.getGameTime();
        long elapsed = scan.lastGrowthGameTime == Long.MIN_VALUE
                ? 40L
                : Math.min(LIFE_MAX_ELAPSED_TICKS, Math.max(0L, gameTime - scan.lastGrowthGameTime));
        scan.lastGrowthGameTime = gameTime;

        double expectedTicks = (LIFE_GROWTH_MULTIPLIER - 1) * VANILLA_RANDOM_TICKS_PER_TICK * elapsed;
        int wholeTicks = (int) expectedTicks;
        double fraction = expectedTicks - wholeTicks;

        LongIterator iterator = scan.plants.iterator();

        while (iterator.hasNext()) {
            BlockPos plantPos = BlockPos.of(iterator.nextLong());

            if (!level.hasChunkAt(plantPos)) {
                continue;
            }

            BlockState state = level.getBlockState(plantPos);

            if (!isLifeBoostedPlant(state)) {
                iterator.remove();
                continue;
            }

            int ticks = wholeTicks + (RANDOM.nextDouble() < fraction ? 1 : 0);

            for (int t = 0; t < ticks; t++) {
                state.randomTick(level, plantPos, RANDOM);
                state = level.getBlockState(plantPos);

                if (!isLifeBoostedPlant(state)) {
                    break; // a sapling became a tree
                }
            }
        }
    }

    /**
     * Bonemeals random positions throughout the full spherical radius, as before the growth
     * boost was added. Unloaded chunks are skipped and never force-loaded.
     */
    private static void randomBonemeal(
            ServerLevel level,
            BlockPos center,
            int radius
    ) {
        double radiusSquared = (double) radius * radius;

        for (int i = 0; i < LIFE_BONEMEAL_ATTEMPTS; i++) {
            int dx;
            int dy;
            int dz;

            // Rejection sampling makes each accepted target fall inside the sphere.
            do {
                dx = RANDOM.nextInt(radius * 2 + 1) - radius;
                dy = RANDOM.nextInt(radius * 2 + 1) - radius;
                dz = RANDOM.nextInt(radius * 2 + 1) - radius;
            } while ((double) dx * dx + (double) dy * dy + (double) dz * dz
                    > radiusSquared);

            BlockPos target = center.offset(dx, dy, dz);

            if (target.getY() < level.getMinY()
                    || target.getY() >= level.getMaxY()
                    || !level.hasChunkAt(target)) {
                continue;
            }

            BlockState state = level.getBlockState(target);

            if (state.getBlock() instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(level, target, state, BonemealSource.INTERACTION)
                    && bonemealable.isBonemealSuccess(level, RANDOM, target, state, BonemealSource.INTERACTION)) {
                bonemealable.performBonemeal(level, RANDOM, target, state, BonemealSource.INTERACTION);
            }
        }
    }

    /**
     * Fully matures baby animals within the entire spherical Life conduit
     * radius. The entity query only sees entities in loaded chunks.
     */
    private static void accelerateBabyGrowth(
            ServerLevel level,
            BlockPos center,
            int radius
    ) {
        if (radius <= 0) {
            return;
        }

        double centerX = center.getX() + 0.5D;
        double centerY = center.getY() + 0.5D;
        double centerZ = center.getZ() + 0.5D;
        double radiusSquared = (double) radius * radius;

        AABB area = new AABB(center).inflate(radius);

        for (AgeableMob baby : level.getEntitiesOfClass(
                AgeableMob.class,
                area,
                entity -> entity.isAlive()
                        && entity.isBaby()
                        && entity.distanceToSqr(centerX, centerY, centerZ)
                        <= radiusSquared
        )) {
            // A very large age-up matures the animal completely.
            baby.ageUp(1_000_000, true);
        }
    }

    /**
     * Reduces animal breeding cooldowns five times faster within
     * the Life conduit radius.
     *
     * Vanilla breeding cooldown: 6000 ticks (5 minutes).
     * Effective cooldown in the conduit: approximately 1200 ticks (1 minute).
     *
     * Only adult animals currently on breeding cooldown are affected.
     */
    private static void accelerateBreedingCooldowns(
            ServerLevel level,
            BlockPos center,
            int radius
    ) {
        if (radius <= 0) {
            return;
        }

        double centerX = center.getX() + 0.5D;
        double centerY = center.getY() + 0.5D;
        double centerZ = center.getZ() + 0.5D;
        double radiusSquared = (double) radius * radius;

        AABB area = new AABB(center).inflate(radius);

        for (Animal animal : level.getEntitiesOfClass(
                Animal.class,
                area,
                entity -> entity.isAlive()
                        && !entity.isBaby()
                        && entity.getAge() > 0
                        && entity.distanceToSqr(
                        centerX,
                        centerY,
                        centerZ
                ) <= radiusSquared
        )) {
            // The conduit is checked every 40 ticks.
            // Vanilla already reduces age naturally; subtract another
            // 160 ticks to achieve 200 ticks of cooldown reduction per
            // 40 ticks, making the cooldown five times faster overall.
            animal.setAge(Math.max(0, animal.getAge() - 160));
        }
    }

    /**
     * Incrementally changes environmental blocks within the spherical radius.
     * The scan traverses a cube but only changes blocks inside the sphere.
     * Unloaded chunks are skipped, never force-loaded.
     */
    private static void localClimateControl(
            ServerLevel level,
            BlockPos center,
            int radius
    ) {
        if (radius <= 0) {
            return;
        }

        ClimateScanKey key = new ClimateScanKey(
                level.dimension(),
                center.immutable()
        );

        ClimateScanState scan = CLIMATE_SCANS.computeIfAbsent(
                key,
                ignored -> new ClimateScanState(radius)
        );

        if (scan.radius != radius) {
            scan.radius = radius;
            scan.cursor = 0;
            scan.lastScanGameTime = Long.MIN_VALUE;
        }

        long gameTime = level.getGameTime();

        if (scan.lastScanGameTime != Long.MIN_VALUE
                && gameTime - scan.lastScanGameTime < CLIMATE_SCAN_INTERVAL_TICKS) {
            return;
        }

        scan.lastScanGameTime = gameTime;

        int diameter = radius * 2 + 1;
        long totalPositions = (long) diameter * diameter * diameter;
        int processed = 0;

        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        long radiusSquared = (long) radius * radius;

        while (processed < CLIMATE_SCAN_BLOCK_BUDGET
                && scan.cursor < totalPositions) {

            long index = scan.cursor++;
            processed++;

            int xIndex = (int) (index % diameter);
            int yIndex = (int) ((index / diameter) % diameter);
            int zIndex = (int) (index / ((long) diameter * diameter));

            int x = center.getX() + xIndex - radius;
            int y = center.getY() + yIndex - radius;
            int z = center.getZ() + zIndex - radius;

            long dx = (long) x - center.getX();
            long dy = (long) y - center.getY();
            long dz = (long) z - center.getZ();

            if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                continue;
            }

            if (y < level.getMinY() || y >= level.getMaxY()) {
                continue;
            }

            check.set(x, y, z);

            if (!level.hasChunkAt(check)) {
                continue;
            }

            BlockState state = level.getBlockState(check);

            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                level.setBlockAndUpdate(
                        check.immutable(),
                        Blocks.AIR.defaultBlockState()
                );
            } else if (state.is(Blocks.SNOW) || state.is(Blocks.POWDER_SNOW)) {
                level.setBlockAndUpdate(
                        check.immutable(),
                        Blocks.AIR.defaultBlockState()
                );
            } else if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
                level.setBlockAndUpdate(
                        check.immutable(),
                        Blocks.WATER.defaultBlockState()
                );
            } else if (state.is(Blocks.LAVA)
                    && state.getFluidState().isSource()) {
                level.setBlockAndUpdate(
                        check.immutable(),
                        Blocks.OBSIDIAN.defaultBlockState()
                );
            }
        }

        if (scan.cursor >= totalPositions) {
            scan.cursor = 0;
        }
    }

    private record ClimateScanKey(
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
    }

    private static final class GrowthScanState {
        private int radius;
        private long cursor;
        private long lastGrowthGameTime = Long.MIN_VALUE;
        private final LongOpenHashSet plants = new LongOpenHashSet();

        private GrowthScanState(int radius) {
            this.radius = radius;
        }
    }

    private static final class ClimateScanState {
        private int radius;
        private long cursor;
        private long lastScanGameTime = Long.MIN_VALUE;

        private ClimateScanState(int radius) {
            this.radius = radius;
        }
    }
}