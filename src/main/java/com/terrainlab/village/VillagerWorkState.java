package com.terrainlab.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Persisted per-villager work state: cooldowns for the profession work behaviours (last delivery to
 * the village chest, last slaughter, last breeding per species key; -1 = never) and the farmer's
 * baking tally. Immutable; update with the {@code with*} methods and store the result back into the
 * attachment.
 * <p>
 * Baking tally: {@code wheatDelivered} is all wheat the farmer has ever brought to the chest
 * (including what went in as bread), {@code wheatBaked} the wheat already turned into bread. Half of
 * all delivered wheat is set aside for baking, so {@link #wheatSetAside()} (= floor(delivered / 2) −
 * baked, always below one loaf's worth after a delivery) is wheat the farmer is holding for the next loaf.
 */
public record VillagerWorkState(long lastDeposit, long lastSlaughter, Map<String, Long> lastBreed,
                                long wheatDelivered, long wheatBaked) {

    public static final VillagerWorkState NONE = new VillagerWorkState(-1L, -1L, Map.of(), 0L, 0L);

    public static final MapCodec<VillagerWorkState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.optionalFieldOf("last_deposit", -1L).forGetter(VillagerWorkState::lastDeposit),
            Codec.LONG.optionalFieldOf("last_slaughter", -1L).forGetter(VillagerWorkState::lastSlaughter),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("last_breed", Map.of())
                    .forGetter(VillagerWorkState::lastBreed),
            Codec.LONG.optionalFieldOf("wheat_delivered", 0L).forGetter(VillagerWorkState::wheatDelivered),
            Codec.LONG.optionalFieldOf("wheat_baked", 0L).forGetter(VillagerWorkState::wheatBaked)
    ).apply(i, VillagerWorkState::new));

    public boolean isEmpty() {
        return lastDeposit < 0L && lastSlaughter < 0L && lastBreed.isEmpty() && wheatDelivered <= 0L && wheatBaked <= 0L;
    }

    public long lastBreed(String species) {
        return lastBreed.getOrDefault(species, -1L);
    }

    /** Wheat set aside for baking but not yet baked (held in the farmer's inventory); never negative. */
    public int wheatSetAside() {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, wheatDelivered / 2L - wheatBaked));
    }

    public VillagerWorkState withLastDeposit(long tick) {
        return new VillagerWorkState(tick, lastSlaughter, lastBreed, wheatDelivered, wheatBaked);
    }

    public VillagerWorkState withLastSlaughter(long tick) {
        return new VillagerWorkState(lastDeposit, tick, lastBreed, wheatDelivered, wheatBaked);
    }

    public VillagerWorkState withLastBreed(String species, long tick) {
        Map<String, Long> copy = new HashMap<>(lastBreed);
        copy.put(species, tick);
        return new VillagerWorkState(lastDeposit, lastSlaughter, Map.copyOf(copy), wheatDelivered, wheatBaked);
    }

    public VillagerWorkState withWheatTally(long delivered, long baked) {
        return new VillagerWorkState(lastDeposit, lastSlaughter, lastBreed, delivered, baked);
    }

    /** True once {@code cooldown} ticks have passed since {@code last} (or it never happened). */
    public static boolean elapsed(long last, long now, long cooldown) {
        return last < 0L || now < last || now - last >= cooldown;
    }
}
