package com.terrainlab.data;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import com.terrainlab.season.Season;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Random;

/**
 * Persists the per-world seasonal offset (see SeasonManager.yearRelativeTick)
 * so each world starts at a different point in the seasonal year.
 */
public class SeasonSavedData extends SavedData {

    public static final SavedDataType<SeasonSavedData> TYPE = new SavedDataType<>(
            "terrainlab_season_offset",
            SeasonSavedData::new,
            level -> RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.point(level),
                    com.mojang.serialization.Codec.LONG.fieldOf("seasonal_offset")
                            .forGetter(sd -> sd.seasonalOffset)
            ).apply(instance, (lvl, offset) -> {
                SeasonSavedData data = new SeasonSavedData(lvl);
                data.seasonalOffset = offset;
                return data;
            }))
    );

    private final ServerLevel level;
    private long seasonalOffset;

    public SeasonSavedData() {
        this.level = null;
        this.seasonalOffset = 0L;
    }

    public SeasonSavedData(ServerLevel level) {
        this.level = level;
        this.seasonalOffset = 0L;
    }

    public static SeasonSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(TYPE);
    }

    public long getSeasonalOffset() {
        return seasonalOffset;
    }

    /**
     * Generates the offset from the world seed if none has been stored yet.
     */
    public void generateOffsetIfAbsent(long worldSeed) {
        if (seasonalOffset != 0L) return;
        // Distinct seed mix from CodexSavedData so the two are uncorrelated.
        Random rng = new Random(worldSeed ^ 0x5345415359L);
        this.seasonalOffset = rng.nextLong(Season.YEAR_LENGTH_TICKS);
        setDirty();
    }
}
