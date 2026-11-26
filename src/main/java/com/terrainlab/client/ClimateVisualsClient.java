package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.particle.BreathParticle;
import com.terrainlab.client.particle.HeatHazeParticle;
import com.terrainlab.registry.ModAttachments;
import com.terrainlab.registry.ModParticles;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.season.SolarTime;
import com.terrainlab.season.TemperatureSeason;
import com.terrainlab.temperature.HeatContributionService;
import com.terrainlab.temperature.LocalTemperatureService;
import com.terrainlab.village.VillagerMoodService;
import com.terrainlab.weather.Sandstorm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Environmental heat and cold, client side: visible breath in cold air, heat haze over sun-baked
 * ground and lava, and midday glare on hot clear days.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class ClimateVisualsClient {

    private static final TagKey<EntityType<?>> NO_VISIBLE_BREATH = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "no_visible_breath"));
    private static final TagKey<Block> HEAT_HAZE_GROUND = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "heat_haze_ground"));

    // Breath: starts below 3 °C, densest from -10 °C.
    private static final double BREATH_START = 3.0D;
    private static final double BREATH_FULL = -10.0D;
    private static final double BREATH_RANGE = 32.0D;
    private static final int BREATH_CHECK_INTERVAL = 10;
    // Ticks between puffs.
    private static final float BREATH_SLOW_INTERVAL = 80.0F;
    private static final float BREATH_FAST_INTERVAL = 30.0F;
    // Mob puffs outlast the gap between breaths, so each one is still fading as the next appears.
    private static final float MOB_PUFF_OVERLAP = 1.4F;
    private static final float PLAYER_BREATH_SCALE = 1.7F;

    // Haze and glare: start at 32 °C, full from 40 °C.
    private static final double HEAT_START = 32.0D;
    private static final double HEAT_FULL = 40.0D;
    private static final int HEAT_SAMPLE_INTERVAL = 20;
    private static final int GROUND_HAZE_RANGE = 20;
    private static final int GROUND_HAZE_TRIES = 6;
    private static final int LAVA_HAZE_RANGE = 12;
    private static final int LAVA_HAZE_TRIES = 4;

    // Solar-time windows {fade-in start, full, fade-out start, fade-out end}.
    private static final long[] HAZE_HOURS = {1500L, 3500L, 9000L, 11000L};
    private static final long[] GLARE_HOURS = {3000L, 5000L, 7000L, 9500L};

    private static final float GLARE_MAX = 0.45F;
    private static final float[] GLARE_COLOUR = {1.0F, 0.96F, 0.86F};

    private static float heatTarget;
    private static float heat;
    private static float exposure = 1.0F;
    private static float glare;

    private ClimateVisualsClient() {
    }

    @SubscribeEvent
    static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BREATH.get(), BreathParticle.Provider::new);
        event.registerSpriteSet(ModParticles.HEAT_HAZE.get(), HeatHazeParticle.Provider::new);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            heatTarget = 0.0F;
            heat = 0.0F;
            exposure = 1.0F;
            glare = 0.0F;
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        long time = level.getGameTime();
        TemperatureSeason season = SeasonManager.getCurrentTemperatureSeason(time, ClientSeasonState.getSeasonalOffset());
        double nightFraction = SolarTime.nightFraction(level);

        tickBreath(minecraft, level, player, time, season, nightFraction);

        BlockPos eye = BlockPos.containing(player.getEyePosition());
        boolean overworld = level.dimension() == Level.OVERWORLD;
        if (time % HEAT_SAMPLE_INTERVAL == 0) {
            heatTarget = overworld && !Sandstorm.isActiveAt(level, eye)
                    ? ramp(weatherTemperature(level, eye, season, nightFraction), HEAT_START, HEAT_FULL)
                    : 0.0F;
        }
        heat += (heatTarget - heat) * 0.02F;
        exposure += ((level.canSeeSky(eye) ? 1.0F : 0.0F) - exposure) * 0.05F;

        float clear = 1.0F - level.getRainLevel(1.0F);
        long solar = overworld ? SolarTime.solarTimeOfDay(level) : 0L;
        float sunHaze = overworld ? window(solar, HAZE_HOURS) : 0.0F;
        glare = heat * clear * exposure * (overworld ? window(solar, GLARE_HOURS) : 0.0F);

        RandomSource random = level.getRandom();
        float groundHaze = heat * clear * sunHaze;
        for (int i = 0; i < GROUND_HAZE_TRIES; i++) {
            if (random.nextFloat() < groundHaze) {
                trySpawnGroundHaze(level, player, random);
            }
        }
        for (int i = 0; i < LAVA_HAZE_TRIES; i++) {
            trySpawnLavaHaze(level, player, random);
        }
    }

    private static void tickBreath(Minecraft minecraft, ClientLevel level, Player player, long time,
                                   TemperatureSeason season, double nightFraction) {
        RandomSource random = level.getRandom();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(BREATH_RANGE))) {

            if (Math.floorMod(time + entity.getId() * 7L, BREATH_CHECK_INTERVAL) != 0
                    || !entity.isAlive()
                    || entity.isInvisible()
                    || entity.isUnderWater()
                    || entity.is(NO_VISIBLE_BREATH)) {
                continue;
            }
            // Cold villagers already puff as part of their mood display.
            if (entity instanceof Villager villager
                    && (villager.getData(ModAttachments.VILLAGER_MOOD_FLAGS.get()) & VillagerMoodService.COLD) != 0) {
                continue;
            }

            BlockPos head = BlockPos.containing(entity.getEyePosition());
            double celsius = LocalTemperatureService.getAmbientTemperature(level, head, season, nightFraction);
            float cold = ramp(-celsius, -BREATH_START, -BREATH_FULL);
            if (cold <= 0.0F) {
                continue;
            }
            float interval = Mth.lerp(cold, BREATH_SLOW_INTERVAL, BREATH_FAST_INTERVAL);
            if (random.nextFloat() < BREATH_CHECK_INTERVAL / interval) {
                spawnBreath(minecraft, level, entity, cold, entity == player, interval);
            }
        }
    }

    private static void spawnBreath(Minecraft minecraft, ClientLevel level, LivingEntity entity,
                                    float cold, boolean self, float interval) {
        Vec3 look = Vec3.directionFromRotation(entity.getXRot(), entity.getYHeadRot());
        boolean firstPerson = self && minecraft.options.getCameraType().isFirstPerson();
        double forward = firstPerson ? 0.6D : entity.getBbWidth() * 0.5D + 0.1D;
        double drop = firstPerson ? 0.3D : entity.getEyeHeight() * 0.12D;
        Vec3 mouth = entity.getEyePosition().add(look.scale(forward)).subtract(0.0D, drop, 0.0D);
        Vec3 drift = look.scale(0.03D);

        Particle particle = minecraft.particleEngine.createParticle(ModParticles.BREATH.get(),
                mouth.x, mouth.y, mouth.z, drift.x, drift.y + 0.004D, drift.z);
        if (particle != null) {
            float size = Mth.clamp(entity.getBbHeight() / 1.8F, 0.5F, 1.6F);
            float playerScale = entity instanceof Player ? PLAYER_BREATH_SCALE : 1.0F;
            if (!(entity instanceof Player)) {
                particle.setLifetime(Math.round(interval * MOB_PUFF_OVERLAP));
            }
            particle.scale(size * playerScale * (0.9F + 0.6F * cold));
        }
    }

    private static void trySpawnGroundHaze(ClientLevel level, Player player, RandomSource random) {
        int x = player.getBlockX() + random.nextInt(GROUND_HAZE_RANGE * 2 + 1) - GROUND_HAZE_RANGE;
        int z = player.getBlockZ() + random.nextInt(GROUND_HAZE_RANGE * 2 + 1) - GROUND_HAZE_RANGE;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos ground = new BlockPos(x, top - 1, z);
        if (!level.isLoaded(ground) || !level.getBlockState(ground).is(HEAT_HAZE_GROUND)
                || !level.getBlockState(ground.above()).isAir()) {
            return;
        }
        level.addParticle(ModParticles.HEAT_HAZE.get(),
                x + random.nextDouble(), top + 0.05D + random.nextDouble() * 0.3D, z + random.nextDouble(),
                0.0D, 0.0D, 0.0D);
    }

    private static void trySpawnLavaHaze(ClientLevel level, Player player, RandomSource random) {
        BlockPos pos = player.blockPosition().offset(
                random.nextInt(LAVA_HAZE_RANGE * 2 + 1) - LAVA_HAZE_RANGE,
                random.nextInt(LAVA_HAZE_RANGE * 2 + 1) - LAVA_HAZE_RANGE,
                random.nextInt(LAVA_HAZE_RANGE * 2 + 1) - LAVA_HAZE_RANGE);
        if (!level.getFluidState(pos).is(FluidTags.LAVA)) {
            return;
        }
        BlockState above = level.getBlockState(pos.above());
        if (!above.isAir()) {
            return;
        }
        level.addParticle(ModParticles.HEAT_HAZE.get(),
                pos.getX() + random.nextDouble(), pos.getY() + 1.0D, pos.getZ() + random.nextDouble(),
                0.0D, 0.0D, 0.0D);
    }

    /** Weather and biome temperature, leaving out campfires, lava and other placed heat sources. */
    private static double weatherTemperature(ClientLevel level, BlockPos pos, TemperatureSeason season,
                                             double nightFraction) {
        return LocalTemperatureService.getAmbientTemperature(level, pos, season, nightFraction)
                - HeatContributionService.getContribution(level, pos);
    }

    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.ATMOSPHERIC || glare <= 0.001F) {
            return;
        }
        // Distant terrain fades into the glare sooner.
        var fog = event.getFogData();
        fog.environmentalStart = Math.min(fog.environmentalStart,
                Mth.lerp(glare * 0.6F, fog.environmentalStart, fog.environmentalEnd * 0.3F));
    }

    @SubscribeEvent
    static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE || glare <= 0.001F) {
            return;
        }
        float k = glare * GLARE_MAX;
        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();
        float light = Mth.clamp((0.299F * r + 0.587F * g + 0.114F * b) / 0.55F, 0.08F, 1.15F);
        event.setRed(Mth.clamp(Mth.lerp(k, r, GLARE_COLOUR[0] * light), 0.0F, 1.0F));
        event.setGreen(Mth.clamp(Mth.lerp(k, g, GLARE_COLOUR[1] * light), 0.0F, 1.0F));
        event.setBlue(Mth.clamp(Mth.lerp(k, b, GLARE_COLOUR[2] * light), 0.0F, 1.0F));
    }

    private static float ramp(double value, double start, double full) {
        float t = (float) Mth.clamp((value - start) / (full - start), 0.0D, 1.0D);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Smooth trapezoid over a solar-time window that may wrap past 24000. */
    private static float window(long t, long[] w) {
        long base = w[0];
        long rise = Math.floorMod(w[1] - base, 24000L);
        long fullEnd = Math.floorMod(w[2] - base, 24000L);
        long fall = Math.floorMod(w[3] - base, 24000L);
        long u = Math.floorMod(t - base, 24000L);
        float linear;
        if (u < rise) {
            linear = u / (float) rise;
        } else if (u < fullEnd) {
            linear = 1.0F;
        } else if (u < fall) {
            linear = 1.0F - (u - fullEnd) / (float) (fall - fullEnd);
        } else {
            return 0.0F;
        }
        return linear * linear * (3.0F - 2.0F * linear);
    }
}
