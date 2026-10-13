package com.terrainlab.client;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModBiomeSets;
import com.terrainlab.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Overworld ambience, replacing per-biome ambient loops. Several looping layers play at once,
 * each with a smoothed volume:
 * <ul>
 *   <li>wind everywhere under open sky, rising with altitude and bad weather, with slow gusts;</li>
 *   <li>surf by distance to the nearest ocean, warm or cold by the ocean's biome;</li>
 *   <li>quiet biome beds blended by how much of the surrounding area each biome covers, with
 *   separate day and night levels.</li>
 * </ul>
 * Occasional wildlife calls are rolled here too, gated by biome and time of day. Everything is
 * scaled by sky exposure, so caves and interiors fall quiet, and fades out quickly when the
 * player's head is underwater.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class AmbienceClient {

    // Biome sampling: a 13x13 grid at 8-block spacing (up to 48 blocks away), once a second.
    private static final int SAMPLE_INTERVAL = 20;
    private static final int SAMPLE_RADIUS = 6;
    private static final int SAMPLE_SPACING = 8;
    private static final double BED_FALLOFF = 56.0;

    private static final double OCEAN_RANGE = 48.0;
    private static final double OCEAN_HEIGHT_ALLOWANCE = 6.0;

    // Per-tick smoothing towards the target volume.
    private static final float FADE_RATE = 0.02F;
    private static final float SUBMERGE_FADE_RATE = 0.25F;

    // Interim gains for the current files, which are mastered loud; raise once the loops are re-levelled.
    private static final float WIND_GAIN = 0.7F;
    private static final float WIND_BASE = 0.12F;
    private static final float OCEAN_COLD_GAIN = 0.3F;
    private static final float OCEAN_WARM_GAIN = 0.8F;

    /** Continuous bed for a group of biomes. A null event means silence at that time of day. */
    private record Bed(Set<ResourceKey<Biome>> biomes,
                       @Nullable Supplier<SoundEvent> day, float dayGain,
                       @Nullable Supplier<SoundEvent> night, float nightGain) {
    }

    // Jungle and swamp reuse their night recordings quietly by day until day recordings are added.
    private static final List<Bed> BEDS = List.of(
            new Bed(ModBiomeSets.ALL_FOREST,
                    ModSounds.BIOME_FOREST_FLORAL::get, 0.5F,
                    null, 0.0F),
            new Bed(ModBiomeSets.JUNGLE,
                    ModSounds.BIOME_JUNGLE_NIGHT::get, 0.2F,
                    ModSounds.BIOME_JUNGLE_NIGHT::get, 0.4F),
            new Bed(ModBiomeSets.WETLANDS,
                    ModSounds.BIOME_SWAMP_NIGHT::get, 0.2F,
                    ModSounds.BIOME_SWAMP_NIGHT::get, 0.4F));

    private static final Set<ResourceKey<Biome>> WARM_OCEANS = Set.of(
            Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN);

    /** Occasional one-shot call. maxDistance 0 plays it non-positionally. */
    private record Call(Set<ResourceKey<Biome>> biomes, boolean atNight, Supplier<SoundEvent> sound,
                        int chance, float volume, double minDistance, double maxDistance) {
    }

    private static final List<Call> CALLS = List.of(
            new Call(ModBiomeSets.OWL_BIOMES, true, ModSounds.OWL_AMBIENT_DISTANT::get, 4800, 1.0F, 10.0, 16.0),
            new Call(ModBiomeSets.TAIGA_WILDLIFE, true, ModSounds.BIOME_WOLVES_DISTANT_NIGHT::get, 4800, 1.0F, 5.0, 9.0),
            new Call(ModBiomeSets.ELEPHANT_BIOMES, true, ModSounds.ELEPHANT_AMBIENT_DISTANT::get, 4800, 0.6F, 0.0, 0.0),
            new Call(ModBiomeSets.VULTURE_BIOMES, false, ModSounds.VULTURE_AMBIENT_DISTANT::get, 4800, 1.0F, 10.0, 16.0),
            new Call(ModBiomeSets.GOOSE_BIOMES, false, ModSounds.GOOSE_AMBIENT_DISTANT::get, 4800, 1.0F, 10.0, 16.0),
            new Call(ModBiomeSets.ALL_FOREST, false, ModSounds.BIOME_FOREST_TEMPERATE::get, 4800, 0.4F, 0.0, 0.0));

    /** Smoothed state for one looping sound. */
    public static final class Layer {
        private final Supplier<SoundEvent> event;
        private float target;
        private float volume;
        private float pitch = 1.0F;
        private @Nullable AmbienceLoopSound sound;
        private int retryCooldown;

        private Layer(Supplier<SoundEvent> event) {
            this.event = event;
        }

        public float volume() {
            return this.volume;
        }

        public float target() {
            return this.target;
        }

        public float pitch() {
            return this.pitch;
        }
    }

    private static final Map<SoundEvent, Layer> LAYERS = new IdentityHashMap<>();
    private static final Map<ResourceKey<Biome>, Bed> BED_BY_BIOME = new HashMap<>();

    static {
        for (Bed bed : BEDS) {
            for (ResourceKey<Biome> key : bed.biomes()) {
                BED_BY_BIOME.put(key, bed);
            }
        }
    }

    // Sampled once a second.
    private static final Map<Bed, Float> bedWeights = new HashMap<>();
    private static double coldOceanDistance = Double.MAX_VALUE;
    private static double warmOceanDistance = Double.MAX_VALUE;
    private static @Nullable Holder<Biome> centreBiome;

    private static float exposure;

    private AmbienceClient() {
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
        boolean submerged = player.isEyeInFluid(FluidTags.WATER);
        BlockPos eye = BlockPos.containing(player.getEyePosition());

        if (overworld && level.getGameTime() % SAMPLE_INTERVAL == 0) {
            sample(level, player);
        }

        float exposureTarget;
        if (level.canSeeSky(eye)) {
            exposureTarget = 1.0F;
        } else {
            // Under leaves sky light stays near 14, so forests stay audible; interiors and caves fall away.
            float sky = level.getBrightness(LightLayer.SKY, eye) / 15.0F;
            exposureTarget = sky * sky * sky;
        }
        exposure += (exposureTarget - exposure) * 0.1F;

        for (Layer layer : LAYERS.values()) {
            layer.target = 0.0F;
        }

        boolean audible = overworld && !submerged;
        if (audible) {
            float day = daylight(level.getOverworldClockTime());
            setWindTarget(level, player, exposure);
            setOceanTargets(level, exposure);
            setBedTargets(day, exposure);
            rollCalls(minecraft, level, player, day, exposure);
        }

        float rate = submerged ? SUBMERGE_FADE_RATE : FADE_RATE;
        for (Layer layer : LAYERS.values()) {
            layer.volume += (Mth.clamp(layer.target, 0.0F, 1.0F) - layer.volume) * rate;
            if (layer.volume < 0.0005F && layer.target <= 0.0F) {
                layer.volume = 0.0F;
            }
            tickSound(minecraft, layer);
        }
    }

    private static Layer layer(Supplier<SoundEvent> event) {
        return LAYERS.computeIfAbsent(event.get(), e -> new Layer(event));
    }

    /** 1 by day, 0 by night, with smooth transitions around dusk and dawn. */
    private static float daylight(long dayTime) {
        float t = dayTime % 24_000L;
        if (t < 11_500F) {
            return 1.0F;
        }
        if (t < 13_000F) {
            return 1.0F - smooth((t - 11_500F) / 1_500F);
        }
        if (t < 22_500F) {
            return 0.0F;
        }
        return smooth((t - 22_500F) / 1_500F);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static void setWindTarget(ClientLevel level, Player player, float exposure) {
        float altitude = (float) (player.getEyeY() - level.getSeaLevel());
        float high = smooth((altitude - 10.0F) / 110.0F);
        float weather = level.getRainLevel(1.0F) * 0.25F + level.getThunderLevel(1.0F) * 0.35F;
        float strength = Mth.clamp(WIND_BASE + (1.0F - WIND_BASE) * high + weather, 0.0F, 1.0F);

        // Slow gusts from two incommensurate sine waves.
        float t = level.getGameTime() / 20.0F;
        float gust = 0.8F + 0.12F * Mth.sin(t * 0.21F) + 0.08F * Mth.sin(t * 0.53F + 1.7F);

        // The sandstorm plays its own wind, so this one steps aside.
        float sandstorm = Mth.clamp(SandstormClient.soundStrength() * 1.5F, 0.0F, 1.0F);

        Layer wind = layer(ModSounds.BIOME_MOUNTAIN_WIND::get);
        wind.target += WIND_GAIN * strength * gust * exposure * exposure * (1.0F - sandstorm);
        wind.pitch = 0.9F + 0.1F * high;
    }

    private static void setOceanTargets(ClientLevel level, float exposure) {
        float storm = 1.0F + level.getRainLevel(1.0F) * 0.2F + level.getThunderLevel(1.0F) * 0.3F;
        layer(ModSounds.BIOME_OCEAN_WAVES_COLD::get).target +=
                OCEAN_COLD_GAIN * proximity(coldOceanDistance) * storm * exposure;
        layer(ModSounds.BIOME_BEACH_WAVES_WARM::get).target +=
                OCEAN_WARM_GAIN * proximity(warmOceanDistance) * storm * exposure;
    }

    private static float proximity(double distance) {
        return smooth((float) (1.0 - distance / OCEAN_RANGE));
    }

    private static void setBedTargets(float day, float exposure) {
        for (Map.Entry<Bed, Float> entry : bedWeights.entrySet()) {
            Bed bed = entry.getKey();
            float weight = entry.getValue();
            if (bed.day() != null) {
                layer(bed.day()).target += weight * bed.dayGain() * day * exposure;
            }
            if (bed.night() != null) {
                layer(bed.night()).target += weight * bed.nightGain() * (1.0F - day) * exposure;
            }
        }
    }

    private static void sample(ClientLevel level, Player player) {
        BlockPos origin = player.blockPosition();
        int y = Math.max(origin.getY(), level.getSeaLevel());
        double heightAboveSea = Math.max(0.0, player.getEyeY() - level.getSeaLevel() - OCEAN_HEIGHT_ALLOWANCE);

        bedWeights.clear();
        double totalWeight = 0.0;
        double cold = Double.MAX_VALUE;
        double warm = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx++) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz++) {
                pos.set(origin.getX() + dx * SAMPLE_SPACING, y, origin.getZ() + dz * SAMPLE_SPACING);
                Holder<Biome> biome = level.getBiome(pos);
                double horizontal = Math.sqrt(dx * dx + dz * dz) * SAMPLE_SPACING;

                if (dx == 0 && dz == 0) {
                    centreBiome = biome;
                }

                if (biome.is(BiomeTags.IS_OCEAN)) {
                    double distance = Math.sqrt(horizontal * horizontal + heightAboveSea * heightAboveSea);
                    if (biome.unwrapKey().map(WARM_OCEANS::contains).orElse(false)) {
                        warm = Math.min(warm, distance);
                    } else {
                        cold = Math.min(cold, distance);
                    }
                }

                double weight = Math.max(0.0, 1.0 - horizontal / BED_FALLOFF);
                if (weight <= 0.0) {
                    continue;
                }
                totalWeight += weight;
                Bed bed = biome.unwrapKey().map(BED_BY_BIOME::get).orElse(null);
                if (bed != null) {
                    bedWeights.merge(bed, (float) weight, Float::sum);
                }
            }
        }

        if (totalWeight > 0.0) {
            float scale = (float) (1.0 / totalWeight);
            bedWeights.replaceAll((bed, w) -> w * scale);
        }
        coldOceanDistance = cold;
        warmOceanDistance = warm;
    }

    private static void rollCalls(Minecraft minecraft, ClientLevel level, Player player, float day, float exposure) {
        if (centreBiome == null || exposure < 0.5F) {
            return;
        }
        RandomSource random = level.getRandom();
        for (Call call : CALLS) {
            boolean timeMatches = call.atNight() ? day < 0.5F : day >= 0.5F;
            if (!timeMatches || random.nextInt(call.chance()) != 0 || !ModBiomeSets.isIn(centreBiome, call.biomes())) {
                continue;
            }
            float volume = call.volume() * exposure;
            float pitch = 0.9F + random.nextFloat() * 0.2F;
            SoundInstance instance;
            if (call.maxDistance() <= 0.0) {
                instance = new SimpleSoundInstance(call.sound().get().location(), SoundSource.AMBIENT, volume, pitch,
                        random, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            } else {
                double angle = random.nextDouble() * Math.PI * 2.0;
                double distance = Mth.lerp(random.nextDouble(), call.minDistance(), call.maxDistance());
                instance = new SimpleSoundInstance(call.sound().get(), SoundSource.AMBIENT, volume, pitch, random,
                        player.getX() + Math.cos(angle) * distance,
                        player.getEyeY() + 4.0 + random.nextDouble() * 6.0,
                        player.getZ() + Math.sin(angle) * distance);
            }
            minecraft.getSoundManager().play(instance);
        }
    }

    private static void tickSound(Minecraft minecraft, Layer layer) {
        if (layer.retryCooldown > 0) {
            layer.retryCooldown--;
        }
        if (layer.target < 0.005F) {
            return;
        }
        if (layer.sound != null && minecraft.getSoundManager().isActive(layer.sound)) {
            return;
        }
        if (layer.retryCooldown > 0) {
            return;
        }
        layer.sound = new AmbienceLoopSound(layer.event.get(), layer);
        minecraft.getSoundManager().play(layer.sound);
        layer.retryCooldown = 40;
    }

    private static void reset() {
        for (Layer layer : LAYERS.values()) {
            layer.target = 0.0F;
            layer.volume = 0.0F;
            layer.sound = null;
        }
        bedWeights.clear();
        coldOceanDistance = Double.MAX_VALUE;
        warmOceanDistance = Double.MAX_VALUE;
        centreBiome = null;
        exposure = 0.0F;
    }
}
