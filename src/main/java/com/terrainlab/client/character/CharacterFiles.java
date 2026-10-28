package com.terrainlab.client.character;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terrainlab.TerrainLab;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Reads (for the character screen) and deletes a character's player files straight from a local
 * world folder, before any server is running. Every read tolerates missing or corrupt files.
 * <p>
 * Locations (26.3): {@code players/data/<uuid>.dat}, {@code players/stats/<uuid>.json},
 * {@code players/advancements/<uuid>.json} (LevelResource.PLAYER_DATA_DIR / PLAYER_STATS_DIR /
 * PLAYER_ADVANCEMENTS_DIR). A world saved by an older version keeps them in {@code playerdata/},
 * {@code stats/} and {@code advancements/} until vanilla's file fixer upgrades it during the open
 * flow (which runs after the character screen), so those are checked as well.
 */
public final class CharacterFiles {

    private static final List<String> DATA_DIRS = List.of("players/data", "playerdata");
    private static final List<String> STATS_DIRS = List.of("players/stats", "stats");
    private static final List<String> ADVANCEMENT_DIRS = List.of("players/advancements", "advancements");
    private static final int SPECTATOR_ID = 3;

    /**
     * What the screen shows for one character. {@code hasData} is false for a character that has
     * never been played (or whose file is unreadable); the other fields are then defaults.
     */
    public record Info(boolean hasData, String dimension, int xpLevel, float health, long playTicks, boolean dead, long fileTime) {
        static final Info NONE = new Info(false, "", 0, 0F, 0L, false, 0L);
    }

    private CharacterFiles() {
    }

    public static Info read(Path worldDir, UUID uuid, boolean hardcore) {
        Path dataFile = firstExisting(worldDir, DATA_DIRS, uuid + ".dat");
        if (dataFile == null) dataFile = firstExisting(worldDir, DATA_DIRS, uuid + ".dat_old");
        if (dataFile == null) return Info.NONE;
        try {
            CompoundTag tag = NbtIo.readCompressed(dataFile, NbtAccounter.unlimitedHeap());
            float health = tag.getFloatOr("Health", 20.0F);
            int level = tag.getIntOr("XpLevel", 0);
            String dimension = tag.getStringOr("Dimension", "minecraft:overworld");
            int gameType = tag.getIntOr("playerGameType", 0);
            boolean dead = hardcore && (health <= 0.0F || gameType == SPECTATOR_ID);
            long fileTime = Files.getLastModifiedTime(dataFile).toMillis();
            return new Info(true, dimension, level, Math.max(0.0F, health), readPlayTicks(worldDir, uuid), dead, fileTime);
        } catch (Exception e) {
            TerrainLab.LOGGER.warn("Could not read character data {}", dataFile, e);
            return Info.NONE;
        }
    }

    /** Ticks played, from the stats file's {@code minecraft:custom / minecraft:play_time}; 0 when unknown. */
    private static long readPlayTicks(Path worldDir, UUID uuid) {
        Path statsFile = firstExisting(worldDir, STATS_DIRS, uuid + ".json");
        if (statsFile == null) return 0L;
        try (Reader reader = Files.newBufferedReader(statsFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject stats = root.getAsJsonObject("stats");
            JsonObject custom = stats == null ? null : stats.getAsJsonObject("minecraft:custom");
            if (custom == null) return 0L;
            JsonElement time = custom.has("minecraft:play_time") ? custom.get("minecraft:play_time") : custom.get("minecraft:play_one_minute");
            return time == null ? 0L : time.getAsLong();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Deletes a character's player files: its player data (and NeoForge's extra per-player files,
     * named {@code <uuid>.<ext>} next to it), stats and advancements. Things it left in the world
     * (pets, placed blocks, villager memories) stay.
     */
    public static void delete(Path worldDir, UUID uuid) {
        String prefix = uuid + ".";
        for (String dir : DATA_DIRS) {
            Path folder = worldDir.resolve(dir);
            if (!Files.isDirectory(folder)) continue;
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, entry -> entry.getFileName().toString().startsWith(prefix))) {
                for (Path file : files) deleteQuietly(file);
            } catch (IOException e) {
                TerrainLab.LOGGER.warn("Could not list {}", folder, e);
            }
        }
        for (String dir : STATS_DIRS) deleteQuietly(worldDir.resolve(dir).resolve(uuid + ".json"));
        for (String dir : ADVANCEMENT_DIRS) deleteQuietly(worldDir.resolve(dir).resolve(uuid + ".json"));
    }

    private static void deleteQuietly(Path file) {
        try {
            if (Files.isRegularFile(file)) Files.delete(file);
        } catch (IOException e) {
            TerrainLab.LOGGER.warn("Could not delete {}", file, e);
        }
    }

    private static Path firstExisting(Path worldDir, List<String> dirs, String fileName) {
        for (String dir : dirs) {
            Path file = worldDir.resolve(dir).resolve(fileName);
            if (Files.isRegularFile(file)) return file;
        }
        return null;
    }
}
