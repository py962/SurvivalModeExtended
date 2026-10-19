package com.terrainlab.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Persisted per-villager cooldowns for the profession work behaviours: last delivery to the village
 * chest, last slaughter, and last breeding per species key. Immutable; update with the {@code with*}
 * methods and store the result back into the attachment. -1 = never.
 */
public record VillagerWorkState(long lastDeposit, long lastSlaughter, Map<String, Long> lastBreed) {

    public static final VillagerWorkState NONE = new VillagerWorkState(-1L, -1L, Map.of());

    public static final MapCodec<VillagerWorkState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.optionalFieldOf("last_deposit", -1L).forGetter(VillagerWorkState::lastDeposit),
            Codec.LONG.optionalFieldOf("last_slaughter", -1L).forGetter(VillagerWorkState::lastSlaughter),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("last_breed", Map.of())
                    .forGetter(VillagerWorkState::lastBreed)
    ).apply(i, VillagerWorkState::new));

    public boolean isEmpty() {
        return lastDeposit < 0L && lastSlaughter < 0L && lastBreed.isEmpty();
    }

    public long lastBreed(String species) {
        return lastBreed.getOrDefault(species, -1L);
    }

    public VillagerWorkState withLastDeposit(long tick) {
        return new VillagerWorkState(tick, lastSlaughter, lastBreed);
    }

    public VillagerWorkState withLastSlaughter(long tick) {
        return new VillagerWorkState(lastDeposit, tick, lastBreed);
    }

    public VillagerWorkState withLastBreed(String species, long tick) {
        Map<String, Long> copy = new HashMap<>(lastBreed);
        copy.put(species, tick);
        return new VillagerWorkState(lastDeposit, lastSlaughter, Map.copyOf(copy));
    }

    /** True once {@code cooldown} ticks have passed since {@code last} (or it never happened). */
    public static boolean elapsed(long last, long now, long cooldown) {
        return last < 0L || now < last || now - last >= cooldown;
    }
}
