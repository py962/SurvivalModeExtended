package com.terrainlab.weather;

import com.terrainlab.TerrainLab;
import com.terrainlab.block.SandLayerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side sandstorm effects in the Overworld (sand layers are handled by SeasonalPrecipitationTickMixin).
 * <ul>
 *   <li>Husks: every STORM_HUSK_INTERVAL_TICKS, each player in a sandstorm may get a tagged husk on open sand
 *   24-48 blocks away, up to 2 nearby (5 when thundering), regardless of light level. Respects Peaceful and
 *   the spawn_mobs gamerule. A minute after the storm, unnamed non-persistent storm husks fade out gradually.</li>
 *   <li>Lapis: when the storm ends, each player in an arid biome gets 3-6 lapis (+2 after thunder) dropped
 *   as ordinary items on open sand 16-40 blocks away.</li>
 * </ul>
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class SandstormSpawner {

    /** Entity tag on husks brought by a sandstorm. */
    public static final String STORM_HUSK_TAG = "terrainlab_storm_husk";

    private static final int STATE_POLL_TICKS = 20;
    private static final int SPOT_TRIES = 12;

    /** Per-level storm bookkeeping; not saved. */
    private static final class StormState {
        boolean raining;
        boolean severe;
        long endedAt = Long.MIN_VALUE;
    }

    private static final Map<ResourceKey<Level>, StormState> STATES = new HashMap<>();

    private SandstormSpawner() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        if (tick % STATE_POLL_TICKS != 0) {
            return;
        }

        ServerLevel level = event.getServer().getLevel(Level.OVERWORLD);
        if (level == null) {
            return;
        }

        StormState state = STATES.computeIfAbsent(level.dimension(), key -> new StormState());
        boolean raining = level.isRaining();

        if (raining) {
            if (!state.raining) {
                state.severe = false;   // a new storm
            }
            state.severe |= level.isThundering();
        } else if (state.raining) {
            state.endedAt = level.getGameTime();
            scatterLapis(level, state.severe);
        }
        state.raining = raining;

        if (tick % WeatherConstants.STORM_HUSK_INTERVAL_TICKS == 0) {
            if (raining) {
                spawnHusks(level);
            } else if (level.getGameTime() - state.endedAt >= WeatherConstants.STORM_HUSK_FADE_AFTER_TICKS) {
                fadeHusks(level);
            }
        }
    }

    private static void spawnHusks(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }

        RandomSource random = level.getRandom();
        int cap = level.isThundering()
                ? WeatherConstants.STORM_HUSK_CAP_SEVERE
                : WeatherConstants.STORM_HUSK_CAP_NORMAL;

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !Sandstorm.isActiveAt(level, player.blockPosition())) {
                continue;
            }

            List<Husk> nearby = level.getEntitiesOfClass(
                    Husk.class,
                    player.getBoundingBox().inflate(WeatherConstants.STORM_HUSK_MAX_DISTANCE),
                    husk -> husk.getTags().contains(STORM_HUSK_TAG));
            if (nearby.size() >= cap) {
                continue;
            }

            BlockPos pos = findOpenSand(level, player.blockPosition(), random,
                    WeatherConstants.STORM_HUSK_MIN_DISTANCE, WeatherConstants.STORM_HUSK_MAX_DISTANCE);
            if (pos == null) {
                continue;
            }

            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;

            // Never right next to anyone (another player may be closer).
            if (level.getNearestPlayer(x, y, z, WeatherConstants.STORM_HUSK_MIN_DISTANCE - 4, false) != null) {
                continue;
            }

            Husk husk = EntityType.HUSK.create(level, EntitySpawnReason.EVENT);
            if (husk == null) {
                continue;
            }
            husk.snapTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
            if (!level.noCollision(husk.getBoundingBox())) {
                continue;
            }

            EventHooks.finalizeMobSpawn(husk, level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
            husk.addTag(STORM_HUSK_TAG);
            level.addFreshEntityWithPassengers(husk);
        }
    }

    /** After the storm, discards a random share of storm husks near players; far ones despawn normally. */
    private static void fadeHusks(ServerLevel level) {
        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            List<Husk> husks = level.getEntitiesOfClass(
                    Husk.class,
                    player.getBoundingBox().inflate(128.0),
                    husk -> husk.getTags().contains(STORM_HUSK_TAG));

            for (Husk husk : husks) {
                if (husk.isRemoved()
                        || husk.hasCustomName()
                        || husk.isPersistenceRequired()
                        || random.nextDouble() >= WeatherConstants.STORM_HUSK_FADE_CHANCE) {
                    continue;
                }
                level.sendParticles(ParticleTypes.POOF,
                        husk.getX(), husk.getY() + husk.getBbHeight() * 0.5, husk.getZ(),
                        12, 0.3, 0.5, 0.3, 0.02);
                husk.discard();
            }
        }
    }

    private static void scatterLapis(ServerLevel level, boolean severe) {
        RandomSource random = level.getRandom();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !Sandstorm.isSandstormBiome(level, player.blockPosition())) {
                continue;
            }

            int count = Mth.nextInt(random, WeatherConstants.STORM_LAPIS_MIN, WeatherConstants.STORM_LAPIS_MAX)
                    + (severe ? WeatherConstants.STORM_LAPIS_SEVERE_BONUS : 0);

            for (int i = 0; i < count; i++) {
                BlockPos pos = findOpenSand(level, player.blockPosition(), random,
                        WeatherConstants.STORM_LAPIS_MIN_DISTANCE, WeatherConstants.STORM_LAPIS_MAX_DISTANCE);
                if (pos == null) {
                    continue;
                }
                ItemEntity item = new ItemEntity(level,
                        pos.getX() + 0.2 + random.nextDouble() * 0.6,
                        pos.getY() + 0.25,
                        pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                        new ItemStack(Items.LAPIS_LAZULI));
                item.setDeltaMovement(
                        (random.nextDouble() - 0.5) * 0.05,
                        0.1,
                        (random.nextDouble() - 0.5) * 0.05);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
    }

    /**
     * Random open-sky sandy spot in a sandstorm biome, minDistance-maxDistance blocks horizontally from centre,
     * in a loaded chunk. Returns the position for feet or item, or null.
     */
    private static @Nullable BlockPos findOpenSand(ServerLevel level, BlockPos centre, RandomSource random,
                                                   int minDistance, int maxDistance) {
        for (int i = 0; i < SPOT_TRIES; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = centre.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = centre.getZ() + Mth.floor(Math.sin(angle) * distance);

            if (!level.hasChunkAt(x, z)) {
                continue;
            }

            // May be a non-collidable sand layer, which is fine to stand in or drop onto.
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);

            if (!level.getFluidState(pos).isEmpty()
                    || !level.canSeeSky(pos)
                    || !Sandstorm.isSandstormBiome(level, pos)
                    || !isSandyGround(level.getBlockState(pos.below()))) {
                continue;
            }
            return pos;
        }
        return null;
    }

    private static boolean isSandyGround(BlockState state) {
        return state.is(BlockTags.SAND)
                || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.SANDSTONE)
                || state.is(Blocks.RED_SANDSTONE)
                || state.getBlock() instanceof SandLayerBlock;
    }
}
