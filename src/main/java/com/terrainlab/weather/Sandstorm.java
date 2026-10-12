package com.terrainlab.weather;

import com.terrainlab.TerrainLab;
import com.terrainlab.temperature.BiomeTemperatureTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared sandstorm rules for client and server. A sandstorm is the global rain state seen from a
 * {@code #terrainlab:has_sandstorm} biome; thunder makes it severe. Biomes that inherit their
 * surroundings (rivers, beaches) take the type of the nearest non-inheriting biome.
 * <p>
 * Nothing is synced: rain/thunder levels and biomes are already on the client, and the wind
 * direction derives from game time.
 */
public final class Sandstorm {

    /** Biomes that get sandstorms (wooded badlands excluded). */
    public static final TagKey<Biome> HAS_SANDSTORM = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(TerrainLab.MODID, "has_sandstorm"));

    private Sandstorm() {
    }

    public static boolean isSandstormBiome(LevelReader level, BlockPos pos) {
        return aridType(level, pos) != NOT_ARID;
    }

    /** Red sand (badlands) rather than ordinary sand. */
    public static boolean isRedSand(LevelReader level, BlockPos pos) {
        return aridType(level, pos) == RED_SAND;
    }

    private static final byte NOT_ARID = 0;
    private static final byte SAND = 1;
    private static final byte RED_SAND = 2;

    /** Ring-search spacing and reach in blocks, matching the temperature blend. */
    private static final int SAMPLE_SPACING = 4;
    private static final int MAX_SEARCH_RADIUS = 48;
    private static final int CACHE_LIMIT = 200_000;

    /** Per-level cache of the inherited type for each 4x4 cell; biomes never change, so entries stay valid. */
    private static final Map<LevelReader, Map<Long, Byte>> INHERITED =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static byte aridType(LevelReader level, BlockPos pos) {
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(HAS_SANDSTORM)) {
            return biome.is(BiomeTags.IS_BADLANDS) ? RED_SAND : SAND;
        }
        if (!inherits(biome)) {
            return NOT_ARID;
        }

        long cell = BlockPos.asLong(pos.getX() >> 2, 0, pos.getZ() >> 2);
        Map<Long, Byte> cache = INHERITED.computeIfAbsent(level, key -> new ConcurrentHashMap<>());
        Byte known = cache.get(cell);
        if (known != null) {
            return known;
        }
        byte found = nearestNeighbourType(level, pos);
        if (cache.size() > CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(cell, found);
        return found;
    }

    private static boolean inherits(Holder<Biome> biome) {
        return biome.unwrapKey()
                .map(ResourceKey::identifier)
                .map(BiomeTemperatureTable.INHERITS_SURROUNDINGS::contains)
                .orElse(false);
    }

    /** Type of the nearest non-inheriting biome found by an outward ring search; NOT_ARID if none in range. */
    private static byte nearestNeighbourType(LevelReader level, BlockPos pos) {
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        for (int r = SAMPLE_SPACING; r <= MAX_SEARCH_RADIUS; r += SAMPLE_SPACING) {
            double bestDistance = Double.MAX_VALUE;
            byte best = NOT_ARID;
            boolean any = false;
            for (int dx = -r; dx <= r; dx += SAMPLE_SPACING) {
                for (int dz = -r; dz <= r; dz += SAMPLE_SPACING) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;   // ring only; inner rings already checked
                    }
                    sample.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
                    Holder<Biome> biome = level.getBiome(sample);
                    if (inherits(biome)) {
                        continue;
                    }
                    double distance = dx * dx + dz * dz;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = biome.is(HAS_SANDSTORM)
                                ? (biome.is(BiomeTags.IS_BADLANDS) ? RED_SAND : SAND)
                                : NOT_ARID;
                        any = true;
                    }
                }
            }
            if (any) {
                return best;
            }
        }
        return NOT_ARID;
    }

    /** True while a sandstorm blows over this position, sheltered or not. Overworld only. */
    public static boolean isActiveAt(Level level, BlockPos pos) {
        return level.dimension() == Level.OVERWORLD
                && level.isRaining()
                && isSandstormBiome(level, pos);
    }

    /** Storm strength 0..1: full rain gives SANDSTORM_NORMAL_STRENGTH, full thunder 1.0. */
    public static float strength(Level level, float partialTick) {
        float rain = level.getRainLevel(partialTick);
        float thunder = level.getThunderLevel(partialTick);
        return rain * Mth.lerp(thunder, WeatherConstants.SANDSTORM_NORMAL_STRENGTH, 1.0F);
    }

    /**
     * Fog distance for an effective strength: eases (in log space) from the vanilla distance to
     * SANDSTORM_NORMAL_VISIBILITY at normal strength, then to SANDSTORM_SEVERE_VISIBILITY at 1.0.
     */
    public static float visibility(float strength, float vanillaDistance) {
        float normal = WeatherConstants.SANDSTORM_NORMAL_VISIBILITY;
        float severe = WeatherConstants.SANDSTORM_SEVERE_VISIBILITY;
        float normalStrength = WeatherConstants.SANDSTORM_NORMAL_STRENGTH;

        if (strength <= 0.0F || vanillaDistance <= severe) {
            return vanillaDistance;
        }

        // Never push fog further out than vanilla's.
        float mid = Math.min(normal, vanillaDistance);

        if (strength <= normalStrength) {
            float t = strength / normalStrength;
            return (float) Math.exp(Mth.lerp(t, Math.log(vanillaDistance), Math.log(mid)));
        }

        float t = Mth.clamp((strength - normalStrength) / (1.0F - normalStrength), 0.0F, 1.0F);
        return (float) Math.exp(Mth.lerp(t, Math.log(mid), Math.log(severe)));
    }

    /** Horizontal wind direction; two slow sine waves of game time, so both sides agree. */
    public static Vec3 windDirection(long gameTime) {
        double t = gameTime;
        double angle = 0.6
                + 1.4 * Math.sin(t / 9000.0)
                + 0.7 * Math.sin(t / 3100.0 + 1.7);
        return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
    }
}
