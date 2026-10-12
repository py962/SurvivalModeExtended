
package com.terrainlab.celestial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-wide record of placed Celestial conduits, keyed by pearl-pair id (or a positional
 * key for unpaired conduits), with each conduit's dimension, position, active state and
 * effect radius. A paired amulet works only while its own sibling conduit is active.
 *
 * <p>Stored on the overworld's DataStorage so it can be queried from any dimension.
 */
public class CelestialConduitRegistry extends SavedData {

    public record ConduitRecord(
            ResourceKey<Level> dimension,
            BlockPos pos,
            boolean active,
            CelestialVariant variant,
            int effectRadius
    ) {
        public static final Codec<ConduitRecord> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Level.RESOURCE_KEY_CODEC.fieldOf("dimension")
                                .forGetter(ConduitRecord::dimension),
                        BlockPos.CODEC.fieldOf("pos")
                                .forGetter(ConduitRecord::pos),
                        Codec.BOOL.fieldOf("active")
                                .forGetter(ConduitRecord::active),
                        CelestialVariant.CODEC.fieldOf("variant")
                                .forGetter(ConduitRecord::variant),
                        Codec.INT.optionalFieldOf("effect_radius", 0)
                                .forGetter(ConduitRecord::effectRadius)
                ).apply(instance, ConduitRecord::new)
        );
    }

    public static final SavedDataType<CelestialConduitRegistry> TYPE = new SavedDataType<>(
            "terrainlab_celestial_conduits",
            CelestialConduitRegistry::new,
            level -> RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.unboundedMap(Codec.STRING, ConduitRecord.CODEC)
                                    .fieldOf("conduits")
                                    .forGetter(data -> {
                                        Map<String, ConduitRecord> out = new HashMap<>();
                                        data.conduits.forEach(
                                                (key, value) -> out.put(key.toString(), value)
                                        );
                                        return out;
                                    })
                    ).apply(instance, map -> {
                        CelestialConduitRegistry data = new CelestialConduitRegistry();
                        map.forEach((key, value) ->
                                data.conduits.put(UUID.fromString(key), value)
                        );
                        return data;
                    })
            )
    );

    private final Map<UUID, ConduitRecord> conduits = new HashMap<>();

    public CelestialConduitRegistry() {
    }

    public CelestialConduitRegistry(ServerLevel level) {
        // Required by SavedDataType.Factory, which passes the level.
    }

    public static CelestialConduitRegistry get(ServerLevel anyLevel) {
        return anyLevel.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Registry key for a conduit: its pair id, or a stable name-based UUID from dimension + position. */
    public static UUID keyFor(
            @javax.annotation.Nullable UUID pairId,
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
        if (pairId != null) {
            return pairId;
        }

        String name = "terrainlab:unpaired_conduit|"
                + dimension.identifier() + "|" + pos.asLong();

        return UUID.nameUUIDFromBytes(
                name.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    public void setConduit(
            UUID pairId,
            ResourceKey<Level> dimension,
            BlockPos pos,
            boolean active,
            CelestialVariant variant,
            int effectRadius
    ) {
        conduits.put(
                pairId,
                new ConduitRecord(dimension, pos.immutable(), active, variant, effectRadius)
        );
        setDirty();
    }

    /**
     * True if an active conduit of this variant has pos within its stored effect radius.
     */
    public boolean hasActiveNearby(
            CelestialVariant variant,
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
        for (ConduitRecord record : conduits.values()) {
            if (record.active()
                    && record.variant() == variant
                    && record.dimension().equals(dimension)
                    && record.effectRadius() > 0
                    && record.pos().distSqr(pos)
                    <= (double) record.effectRadius() * record.effectRadius()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the active conduit of the requested variant that provides the strongest
     * distance-based effect at a position.
     *
     * <p>Strength is calculated as 1 - (distance / radius), clamped to the range
     * 0..1. A conduit at its centre has strength 1; the effect falls to zero at
     * the edge of its radius.
     *
     * <p>Only conduits in the requested dimension are considered.
     */
    public Optional<ConduitRecord> findStrongestActiveNearby(
            CelestialVariant variant,
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
        ConduitRecord strongest = null;
        double strongestStrength = 0.0;

        for (ConduitRecord record : conduits.values()) {
            if (!record.active()
                    || record.variant() != variant
                    || !record.dimension().equals(dimension)
                    || record.effectRadius() <= 0) {
                continue;
            }

            double distanceSquared = record.pos().distSqr(pos);
            double radius = record.effectRadius();

            if (distanceSquared > radius * radius) {
                continue;
            }

            double distance = Math.sqrt(distanceSquared);
            double strength = 1.0 - distance / radius;

            if (strength > strongestStrength) {
                strongestStrength = strength;
                strongest = record;
            }
        }

        return Optional.ofNullable(strongest);
    }

    /** Like hasActiveNearby, but only counts conduits at full-frame radius. */
    public boolean hasFullPowerNearby(
            CelestialVariant variant,
            ResourceKey<Level> dimension,
            BlockPos pos
    ) {
        int fullRadius = CelestialFrame.rangeFor(CelestialFrame.FULL_FRAME);

        for (ConduitRecord record : conduits.values()) {
            if (record.active()
                    && record.variant() == variant
                    && record.dimension().equals(dimension)
                    && record.effectRadius() >= fullRadius
                    && record.pos().distSqr(pos)
                    <= (double) record.effectRadius() * record.effectRadius()) {
                return true;
            }
        }

        return false;
    }

    /** True if any active conduit of this variant exists in any dimension. */
    public boolean hasAnyActive(CelestialVariant variant) {
        for (ConduitRecord record : conduits.values()) {
            if (record.active() && record.variant() == variant) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a worn amulet is switched on. A paired amulet needs its own conduit active;
     * an unpaired amulet works only for creative-mode wearers while any conduit of its
     * variant is active.
     */
    public static boolean isAmuletActive(
            net.minecraft.server.level.ServerPlayer player,
            ServerLevel level,
            @javax.annotation.Nullable UUID pairId,
            CelestialVariant variant
    ) {
        CelestialConduitRegistry registry = get(level);

        if (pairId != null) {
            return registry.isActive(pairId);
        }

        return player.isCreative() && registry.hasAnyActive(variant);
    }

    public void removeConduit(UUID pairId) {
        if (conduits.remove(pairId) != null) {
            setDirty();
        }
    }

    public Optional<ConduitRecord> get(UUID pairId) {
        return Optional.ofNullable(conduits.get(pairId));
    }

    public boolean isActive(UUID pairId) {
        ConduitRecord record = conduits.get(pairId);
        return record != null && record.active();
    }

    /** Nearest active conduit of the variant, preferring the given dimension and falling back to any dimension. */
    public Optional<ConduitRecord> findNearestActive(
            CelestialVariant variant,
            ResourceKey<Level> preferredDimension,
            BlockPos near
    ) {
        ConduitRecord best = null;
        double bestDistSq = Double.MAX_VALUE;
        ConduitRecord bestInPreferred = null;
        double bestInPreferredDistSq = Double.MAX_VALUE;

        for (ConduitRecord record : conduits.values()) {
            if (!record.active() || record.variant() != variant) {
                continue;
            }

            if (record.dimension().equals(preferredDimension)) {
                double distSq = record.pos().distSqr(near);

                if (distSq < bestInPreferredDistSq) {
                    bestInPreferredDistSq = distSq;
                    bestInPreferred = record;
                }
            }

            double anyDistSq = record.pos().distSqr(near);

            if (anyDistSq < bestDistSq) {
                bestDistSq = anyDistSq;
                best = record;
            }
        }

        return Optional.ofNullable(bestInPreferred != null ? bestInPreferred : best);
    }
}