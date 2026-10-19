package com.terrainlab.season.plants;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-chunk seasonal ground-plant state (serialized with the chunk, only when non-empty):
 * <ul>
 *   <li>{@code playerPlaced}: packed positions of seasonal plants placed (or bonemeal-grown) by players
 *       or other entities; these are never made dormant.</li>
 *   <li>{@code dormant}: naturally generated plants removed while out of season, keyed by packed
 *       position, with the state to restore (the lower half for tall flowers).</li>
 * </ul>
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
            DormantEntry.CODEC.listOf().optionalFieldOf("dormant", List.of()).forGetter(SeasonalPlantChunkData::dormantList)
    ).apply(i, SeasonalPlantChunkData::fromLists));

    final LongOpenHashSet playerPlaced = new LongOpenHashSet();
    final Long2ObjectOpenHashMap<BlockState> dormant = new Long2ObjectOpenHashMap<>();

    public SeasonalPlantChunkData() {
    }

    private static SeasonalPlantChunkData fromLists(List<Long> placed, List<DormantEntry> dormantEntries) {
        SeasonalPlantChunkData data = new SeasonalPlantChunkData();
        data.playerPlaced.addAll(placed);
        for (DormantEntry entry : dormantEntries) {
            data.dormant.put(entry.pos(), entry.state());
        }
        return data;
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
        return playerPlaced.isEmpty() && dormant.isEmpty();
    }

    public boolean isPlayerPlaced(long pos) {
        return playerPlaced.contains(pos);
    }

    public int dormantCount() {
        return dormant.size();
    }
}
