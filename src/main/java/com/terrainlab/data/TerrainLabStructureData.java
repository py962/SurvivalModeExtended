package com.terrainlab.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tracks structure positions already post-processed (temples, monument rooms, outlaw camps, pirate coves). */
public class TerrainLabStructureData extends SavedData {

    private final Set<Long> processedTemples = new HashSet<>();
    private final Set<Long> processedMonumentRooms = new HashSet<>();
    private final Set<Long> processedOutlawCamps = new HashSet<>();
    private final Set<Long> processedPirateCoves = new HashSet<>();

    // Matches SavedDataType.Factory's ServerLevel parameter; the level is unused.
    public TerrainLabStructureData(ServerLevel level) {
    }

    public static final SavedDataType<TerrainLabStructureData> TYPE = new SavedDataType<TerrainLabStructureData>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.terrainlab.TerrainLab.MODID, "structure_tracking"),
            TerrainLabStructureData::new,
            level -> RecordCodecBuilder.<TerrainLabStructureData>create(instance -> instance.group(
                    Codec.LONG.listOf().fieldOf("processed_temples")
                            .forGetter((TerrainLabStructureData sd) -> List.copyOf(sd.processedTemples)),
                    Codec.LONG.listOf().fieldOf("processed_monument_rooms")
                            .forGetter((TerrainLabStructureData sd) -> List.copyOf(sd.processedMonumentRooms)),
                    Codec.LONG.listOf().optionalFieldOf("processed_outlaw_camps", List.of())
                            .forGetter((TerrainLabStructureData sd) -> List.copyOf(sd.processedOutlawCamps)),
                    Codec.LONG.listOf().optionalFieldOf("processed_pirate_coves", List.of())
                            .forGetter((TerrainLabStructureData sd) -> List.copyOf(sd.processedPirateCoves))
            ).apply(instance, (temples, monuments, outlawCamps, pirateCoves) -> {
                TerrainLabStructureData data = new TerrainLabStructureData(level);
                data.processedTemples.addAll(temples);
                data.processedMonumentRooms.addAll(monuments);
                data.processedOutlawCamps.addAll(outlawCamps);
                data.processedPirateCoves.addAll(pirateCoves);
                return data;
            }))
    );

    public static TerrainLabStructureData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean isTempleProcessed(BlockPos pos) {
        return processedTemples.contains(pos.asLong());
    }

    public void markTempleProcessed(BlockPos pos) {
        processedTemples.add(pos.asLong());
        setDirty();
    }

    public boolean isMonumentRoomProcessed(BlockPos pos) {
        return processedMonumentRooms.contains(pos.asLong());
    }

    public void markMonumentRoomProcessed(BlockPos pos) {
        processedMonumentRooms.add(pos.asLong());
        setDirty();
    }

    public boolean isOutlawCampProcessed(BlockPos pos) {
        return processedOutlawCamps.contains(pos.asLong());
    }

    public void markOutlawCampProcessed(BlockPos pos) {
        processedOutlawCamps.add(pos.asLong());
        setDirty();
    }

    public boolean isPirateCoveProcessed(BlockPos pos) {
        return processedPirateCoves.contains(pos.asLong());
    }

    public void markPirateCoveProcessed(BlockPos pos) {
        processedPirateCoves.add(pos.asLong());
        setDirty();
    }
}
