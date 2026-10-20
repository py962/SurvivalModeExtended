package com.terrainlab.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.LongStream;

/**
 * Persisted Nomad progress for one player: the chunks they have completed a sleep in, as packed
 * ChunkPos longs kept sorted per dimension id (stored as NBT long arrays), and whether a completed
 * sleep ever landed in a chunk already in the set. Immutable; store the result of
 * {@link #withSleep} back into the attachment.
 */
public final class NomadSleepRecord {

    public static final NomadSleepRecord EMPTY = new NomadSleepRecord(Map.of(), false);

    private static final Codec<long[]> LONG_ARRAY = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);

    public static final MapCodec<NomadSleepRecord> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.unboundedMap(Codec.STRING, LONG_ARRAY).optionalFieldOf("chunks", Map.of())
                    .forGetter(r -> r.chunks),
            Codec.BOOL.optionalFieldOf("repeated", false).forGetter(r -> r.repeated)
    ).apply(i, NomadSleepRecord::sorted));

    private final Map<String, long[]> chunks;
    private final boolean repeated;

    private NomadSleepRecord(Map<String, long[]> chunks, boolean repeated) {
        this.chunks = chunks;
        this.repeated = repeated;
    }

    private static NomadSleepRecord sorted(Map<String, long[]> chunks, boolean repeated) {
        Map<String, long[]> copy = new HashMap<>();
        chunks.forEach((dim, longs) -> copy.put(dim, LongStream.of(longs).sorted().distinct().toArray()));
        return new NomadSleepRecord(Map.copyOf(copy), repeated);
    }

    public boolean isEmpty() {
        return chunks.isEmpty() && !repeated;
    }

    /** True once a completed sleep happened in a chunk already slept in. */
    public boolean repeated() {
        return repeated;
    }

    public boolean contains(String dimension, long chunk) {
        long[] longs = chunks.get(dimension);
        return longs != null && Arrays.binarySearch(longs, chunk) >= 0;
    }

    /** Records a completed sleep; sets {@link #repeated()} if the chunk was already in the set. */
    public NomadSleepRecord withSleep(String dimension, long chunk) {
        long[] longs = chunks.getOrDefault(dimension, new long[0]);
        int index = Arrays.binarySearch(longs, chunk);
        if (index >= 0) {
            return repeated ? this : new NomadSleepRecord(chunks, true);
        }
        int insert = -index - 1;
        long[] grown = new long[longs.length + 1];
        System.arraycopy(longs, 0, grown, 0, insert);
        grown[insert] = chunk;
        System.arraycopy(longs, insert, grown, insert + 1, longs.length - insert);
        Map<String, long[]> copy = new HashMap<>(chunks);
        copy.put(dimension, grown);
        return new NomadSleepRecord(Map.copyOf(copy), repeated);
    }
}
