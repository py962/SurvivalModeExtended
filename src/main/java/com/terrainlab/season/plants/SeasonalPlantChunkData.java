package com.terrainlab.season.plants;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-chunk seasonal ground-plant state (serialized with the chunk, only when non-empty):
 * <ul>
 *   <li>{@code playerPlaced}: packed positions of seasonal plants placed (or bonemeal-grown) by players
 *       or other entities; these are never made dormant.</li>
 *   <li>{@code dormant}: naturally generated plants removed while out of season, keyed by packed
 *       position, with the state to restore (the lower half for tall flowers).</li>
 *   <li>{@code extras}: packed positions of the in-season boost plants (present or dormant). They are
 *       natural in every other respect (they go dormant and come back like the rest), but are left out of
 *       the natural count the boost is based on, so it never compounds.</li>
 *   <li>{@code boosted}: per category name, the in-season run (absolute calendar-state index of its first
 *       state) in which this chunk last received its boost.</li>
 * </ul>
 * All fields are optional in the codec, so chunks saved before a field existed load unchanged.
 * Mutated in place; callers must {@code markUnsaved()} the chunk after a change.
 */
public final class SeasonalPlantChunkData {

    private record DormantEntry(long pos, BlockState state) {
        static final Codec<DormantEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(DormantEntry::pos),
                BlockState.CODEC.fieldOf("state").forGetter(DormantEntry::state)
        ).apply(i, DormantEntry::new));
    }

    public static final MapCodec<SeasonalPlantChunkData> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.LONG.listOf().optionalFieldOf("player_placed", List.of()).forGetter(SeasonalPlantChunkData::playerPlacedList),
            DormantEntry.CODEC.listOf().optionalFieldOf("dormant", List.of()).forGetter(SeasonalPlantChunkData::dormantList),
            Codec.LONG.listOf().optionalFieldOf("extras", List.of()).forGetter(SeasonalPlantChunkData::extrasList),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("boosted", Map.of()).forGetter(SeasonalPlantChunkData::boostedMap)
    ).apply(i, SeasonalPlantChunkData::fromLists));

    final LongOpenHashSet playerPlaced = new LongOpenHashSet();
    final Long2ObjectOpenHashMap<BlockState> dormant = new Long2ObjectOpenHashMap<>();
    final LongOpenHashSet extras = new LongOpenHashSet();
    final Object2LongOpenHashMap<String> boosted = new Object2LongOpenHashMap<>();

    public SeasonalPlantChunkData() {
    }

    private static SeasonalPlantChunkData fromLists(List<Long> placed, List<DormantEntry> dormantEntries,
                                                    List<Long> extraList, Map<String, Long> boostedRuns) {
        SeasonalPlantChunkData data = new SeasonalPlantChunkData();
        data.playerPlaced.addAll(placed);
        for (DormantEntry entry : dormantEntries) {
            data.dormant.put(entry.pos(), entry.state());
        }
        data.extras.addAll(extraList);
        data.boosted.putAll(boostedRuns);
        return data;
    }

    private List<Long> extrasList() {
        return new ArrayList<>(extras);
    }

    private Map<String, Long> boostedMap() {
        return new HashMap<>(boosted);
    }

    /** The run in which {@code category} was last boosted here, or {@code Long.MIN_VALUE}. */
    long boostedRun(SeasonalPlantCategory category) {
        return boosted.containsKey(category.name()) ? boosted.getLong(category.name()) : Long.MIN_VALUE;
    }

    void setBoostedRun(SeasonalPlantCategory category, long run) {
        boosted.put(category.name(), run);
    }

    private List<Long> playerPlacedList() {
        return new ArrayList<>(playerPlaced);
    }

    private List<DormantEntry> dormantList() {
        List<DormantEntry> list = new ArrayList<>(dormant.size());
        for (Long2ObjectMap.Entry<BlockState> entry : dormant.long2ObjectEntrySet()) {
            list.add(new DormantEntry(entry.getLongKey(), entry.getValue()));
        }
        return list;
    }

    public boolean isEmpty() {
        return playerPlaced.isEmpty() && dormant.isEmpty() && extras.isEmpty() && boosted.isEmpty();
    }

    public boolean isPlayerPlaced(long pos) {
        return playerPlaced.contains(pos);
    }

    public int dormantCount() {
        return dormant.size();
    }
}
