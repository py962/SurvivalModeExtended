package com.terrainlab.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Per-level store of {@link VillageRecord}s keyed by {@link VillageId}. Persisted as a list of
 * (dimension, bell position, record) entries; callers must call {@code setDirty()} after mutating
 * a record.
 */
public class VillageSavedData extends SavedData {

    private final Map<VillageId, VillageRecord> records = new HashMap<>();

    private record Entry(ResourceKey<Level> dimension, BlockPos bellPos, VillageRecord record) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Entry::dimension),
                BlockPos.CODEC.fieldOf("bell_pos").forGetter(Entry::bellPos),
                VillageRecord.CODEC.fieldOf("record").forGetter(Entry::record)
        ).apply(instance, Entry::new));
    }

    public static final SavedDataType<VillageSavedData> TYPE = new SavedDataType<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.terrainlab.TerrainLab.MODID, "villages"),
            VillageSavedData::new,
            level -> RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.point(level),
                    Entry.CODEC.listOf().fieldOf("villages").forGetter(
                            (VillageSavedData sd) -> sd.records.entrySet().stream()
                                    .map(en -> new Entry(en.getKey().dimension(), en.getKey().bellPos(), en.getValue()))
                                    .toList())
            ).apply(instance, (lvl, entries) -> {
                VillageSavedData data = new VillageSavedData(lvl);
                for (Entry e : entries) {
                    data.records.put(new VillageId(e.dimension(), e.bellPos()), e.record());
                }
                return data;
            }))
    );

    public VillageSavedData() {
    }

    public VillageSavedData(ServerLevel level) {
        // Level-aware constructor required by the SavedDataType factory; the level is unused.
    }

    public static VillageSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public VillageRecord getOrCreate(VillageId id) {
        return records.computeIfAbsent(id, k -> new VillageRecord());
    }

    /** The record for a village if one exists, without creating it. */
    public java.util.Optional<VillageRecord> find(VillageId id) {
        return java.util.Optional.ofNullable(records.get(id));
    }

    public Set<VillageId> knownVillageIds() {
        return records.keySet();
    }

    /** Forgets a village, e.g. when its bell is gone. */
    public void remove(VillageId id) {
        if (records.remove(id) != null) {
            setDirty();
        }
    }
}
