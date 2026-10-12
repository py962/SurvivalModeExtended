package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.client.particle.SandstormParticle;
import com.terrainlab.registry.ModParticles;
import com.terrainlab.weather.Sandstorm;
import com.terrainlab.weather.WeatherConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client side of sandstorms (rules live in {@link Sandstorm}). Each tick it tracks a
 * smoothed storm level, the share of nearby sampled terrain that is a sandstorm biome
 * (so the storm fades in over about 30 blocks), and sky exposure. These drive the fog
 * distance and tint, the blowing-sand particles and the wind sound.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class SandstormClient {

    private static final float[] SAND_FOG = {0.80F, 0.67F, 0.46F};
    private static final float[] RED_SAND_FOG = {0.70F, 0.42F, 0.25F};

    private static final int SAMPLE_INTERVAL = 10;
    private static final int SAMPLE_RADIUS = 2;      // 5x5 grid
    private static final int SAMPLE_SPACING = 8;

    private static final float GRAINS_PER_TICK = 40.0F;
    private static final float PUFFS_PER_TICK = 3.0F;
    private static final double SPAWN_HALF_WIDTH = 20.0;

    // Targets (sampled) and smoothed values.
    private static float biomeTarget;
    private static float badlandsTarget;
    private static float biomeFraction;
    private static float badlandsFraction;
    private static float exposure = 1.0F;

    private static float stormLevel;
    private static float thunderLevel;

    private static @Nullable SandstormSoundInstance sound;
    private static int soundRetryCooldown;

    private SandstormClient() {
    }

    /** Storm strength out in the open here (no shelter applied). */
    public static float outdoorStrength() {
        return stormLevel * biomeFraction;
    }

    /** Strength used for fog: indoors only a light haze remains. */
    public static float fogStrength() {
        return outdoorStrength() * Mth.lerp(exposure, WeatherConstants.SANDSTORM_SHELTERED_FACTOR, 1.0F);
    }

    /** Strength for the wind sound: muffled indoors, never silent. */
    public static float soundStrength() {
        return outdoorStrength() * Mth.lerp(exposure, 0.45F, 1.0F);
    }

    /** 0..1, how far towards a severe (thunder) storm it is here. */
    public static float severity() {
        return thunderLevel * biomeFraction;
    }

    @SubscribeEvent
    static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SANDSTORM_GRAIN.get(),
                sprites -> new SandstormParticle.Provider(sprites, SandstormParticle.Kind.GRAIN));
        event.registerSpriteSet(ModParticles.SANDSTORM_PUFF.get(),
                sprites -> new SandstormParticle.Provider(sprites, SandstormParticle.Kind.PUFF));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;

        if (level == null || player == null) {
            reset();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }

        boolean overworld = level.dimension() == Level.OVERWORLD;
        BlockPos eye = BlockPos.containing(player.getEyePosition());

        if (overworld && level.getGameTime() % SAMPLE_INTERVAL == 0) {
            sampleBiomes(level, player.blockPosition());
        }
        if (!overworld) {
            biomeTarget = 0.0F;
            badlandsTarget = 0.0F;
        }

        stormLevel = overworld ? Sandstorm.strength(level, 1.0F) : 0.0F;
        thunderLevel = overworld ? level.getThunderLevel(1.0F) : 0.0F;

        float exposureTarget;
        if (level.canSeeSky(eye)) {
            exposureTarget = 1.0F;
        } else {
            float sky = level.getBrightness(LightLayer.SKY, eye) / 15.0F;
            exposureTarget = 0.6F * sky * sky;
        }

        // Roughly 2-3 s to settle.
        biomeFraction += (biomeTarget - biomeFraction) * 0.05F;
        badlandsFraction += (badlandsTarget - badlandsFraction) * 0.05F;
        exposure += (exposureTarget - exposure) * 0.1F;

        float outdoor = outdoorStrength();
        if (outdoor > 0.02F) {
            spawnParticles(level, player, outdoor);
        }

        tickSound(minecraft);
    }

    private static void sampleBiomes(ClientLevel level, BlockPos centre) {
        int arid = 0;
        int badlands = 0;
        int total = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx++) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz++) {
                pos.set(centre.getX() + dx * SAMPLE_SPACING, centre.getY(), centre.getZ() + dz * SAMPLE_SPACING);
                total++;
                // Rivers/beaches count as whatever their neighbours are.
                if (Sandstorm.isSandstormBiome(level, pos)) {
                    arid++;
                    if (Sandstorm.isRedSand(level, pos)) {
                        badlands++;
                    }
                }
            }
        }

        biomeTarget = arid / (float) total;
        badlandsTarget = arid == 0 ? badlandsTarget : badlands / (float) arid;
    }

    private static void spawnParticles(ClientLevel level, Player player, float outdoor) {
        RandomSource random = level.getRandom();
        Vec3 wind = Sandstorm.windDirection(level.getGameTime());
        float speed = 0.3F + 0.4F * severity();

        // Start a little upwind so the sand streams past the player.
        double cx = player.getX() - wind.x * 8.0;
        double cz = player.getZ() - wind.z * 8.0;

        int grains = count(random, outdoor * GRAINS_PER_TICK);
        int puffs = count(random, outdoor * PUFFS_PER_TICK);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < grains + puffs; i++) {
            boolean puff = i >= grains;
            double x = cx + (random.nextDouble() * 2.0 - 1.0) * SPAWN_HALF_WIDTH;
            double z = cz + (random.nextDouble() * 2.0 - 1.0) * SPAWN_HALF_WIDTH;
            double y = player.getY() + (puff ? random.nextDouble() * 6.0 - 1.0 : random.nextDouble() * 9.0 - 3.0);

            pos.set(x, y, z);
            // Open sky only, and only over sandstorm biomes.
            if (y < level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())
                    || !Sandstorm.isSandstormBiome(level, pos)) {
                continue;
            }

            double v = speed * (0.8 + random.nextDouble() * 0.4) * (puff ? 0.7 : 1.0);
            level.addParticle(
                    puff ? ModParticles.SANDSTORM_PUFF.get() : ModParticles.SANDSTORM_GRAIN.get(),
                    x, y, z,
                    wind.x * v + (random.nextDouble() - 0.5) * 0.04,
                    (random.nextDouble() - 0.35) * 0.04,
                    wind.z * v + (random.nextDouble() - 0.5) * 0.04);
        }
    }

    /** Whole part, plus one more with the fractional part as the chance. */
    private static int count(RandomSource random, float expected) {
        int whole = (int) expected;
        return whole + (random.nextFloat() < expected - whole ? 1 : 0);
    }

    private static void tickSound(Minecraft minecraft) {
        if (soundRetryCooldown > 0) {
            soundRetryCooldown--;
        }
        if (soundStrength() < 0.02F) {
            return;
        }
        if (sound != null && minecraft.getSoundManager().isActive(sound)) {
            return;
        }
        if (soundRetryCooldown > 0) {
            return;
        }
        sound = new SandstormSoundInstance();
        minecraft.getSoundManager().play(sound);
        soundRetryCooldown = 40;
    }

    private static void reset() {
        biomeTarget = 0.0F;
        badlandsTarget = 0.0F;
        biomeFraction = 0.0F;
        badlandsFraction = 0.0F;
        exposure = 1.0F;
        stormLevel = 0.0F;
        thunderLevel = 0.0F;
        sound = null;
    }

    @SubscribeEvent
    static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.ATMOSPHERIC) {
            return;
        }
        float k = fogStrength();
        if (k <= 0.001F) {
            return;
        }

        var fog = event.getFogData();
        float end = Sandstorm.visibility(k, fog.environmentalEnd);
        float blend = Mth.clamp(k / WeatherConstants.SANDSTORM_NORMAL_STRENGTH, 0.0F, 1.0F);

        fog.environmentalEnd = end;
        // Haze right up to the camera in a heavy storm.
        fog.environmentalStart = Math.min(fog.environmentalStart, -end * 0.5F * blend);
        fog.skyEnd = Math.min(fog.skyEnd, Mth.lerp(blend, fog.skyEnd, end));
        fog.cloudEnd = Math.min(fog.cloudEnd, Mth.lerp(blend, fog.cloudEnd, end));
    }

    @SubscribeEvent
    static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float k = Mth.clamp(fogStrength() * 1.6F, 0.0F, 1.0F);
        if (k <= 0.001F) {
            return;
        }

        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();

        // Keep vanilla's daylight level (night, dusk, storm darkening).
        float light = Mth.clamp((0.299F * r + 0.587F * g + 0.114F * b) / 0.55F, 0.08F, 1.1F);

        float sr = Mth.lerp(badlandsFraction, SAND_FOG[0], RED_SAND_FOG[0]) * light;
        float sg = Mth.lerp(badlandsFraction, SAND_FOG[1], RED_SAND_FOG[1]) * light;
        float sb = Mth.lerp(badlandsFraction, SAND_FOG[2], RED_SAND_FOG[2]) * light;

        event.setRed(Mth.clamp(Mth.lerp(k, r, sr), 0.0F, 1.0F));
        event.setGreen(Mth.clamp(Mth.lerp(k, g, sg), 0.0F, 1.0F));
        event.setBlue(Mth.clamp(Mth.lerp(k, b, sb), 0.0F, 1.0F));
    }
}
