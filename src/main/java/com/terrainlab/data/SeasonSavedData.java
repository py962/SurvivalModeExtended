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
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.terrainlab.TerrainLab.MODID, "season_offset"),
            SeasonSavedData::new,
            level -> RecordCodecBuilder.create(instance -> instance.group(
                    RecordCodecBuilder.point(level),
                    com.mojang.serialization.Codec.LONG.fieldOf("seasonal_offset")
                            .forGetter(sd -> sd.seasonalOffset),
                    com.mojang.serialization.Codec.INT.optionalFieldOf("year_length_days", Season.DEFAULT_YEAR_LENGTH_DAYS)
                            .forGetter(sd -> sd.yearLengthDays)
            ).apply(instance, (lvl, offset, yearDays) -> {
                SeasonSavedData data = new SeasonSavedData(lvl);
                data.seasonalOffset = offset;
                data.yearLengthDays = yearDays;
                return data;
            }))
    );

    private final ServerLevel level;
    private long seasonalOffset;
    private int yearLengthDays = Season.DEFAULT_YEAR_LENGTH_DAYS;

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

    /** The world's seasonal year length in days (60 to 360); worlds that never set one have 60. */
    public int getYearLengthDays() {
        return Season.isValidYearLength(yearLengthDays) ? yearLengthDays : Season.DEFAULT_YEAR_LENGTH_DAYS;
    }

    public void setYearLengthDays(int days) {
        this.yearLengthDays = days;
        setDirty();
    }

    /**
     * Sets the offset (the /terrainlab season command). 0 means "not generated yet" to
     * generateOffsetIfAbsent, so a zero offset is stored as one full year, which is equivalent.
     */
    public void setSeasonalOffset(long offset) {
        long normalised = Math.floorMod(offset, Season.yearLengthTicks());
        this.seasonalOffset = normalised == 0L ? Season.yearLengthTicks() : normalised;
        setDirty();
    }

    /**
     * Generates the offset from the world seed if none has been stored yet.
     */
    public void generateOffsetIfAbsent(long worldSeed) {
        if (seasonalOffset != 0L) return;
        // Distinct seed mix from CodexSavedData so the two are uncorrelated.
        Random rng = new Random(worldSeed ^ 0x5345415359L);
        this.seasonalOffset = rng.nextLong(Season.yearLengthTicks());
        setDirty();
    }
}
