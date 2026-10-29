package com.terrainlab.character;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.terrainlab.TerrainLab;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The characters of one singleplayer world, stored in {@code <world>/terrainlab_characters.json}.
 * <p>
 * Each character is a separate player to the game, identified only by its UUID: inventory,
 * position, spawn point, stats, advancements, ender chest, attachments, pets, reputation… all
 * live under that UUID in the world's player files, so this registry only needs the list of
 * UUIDs plus a display name and two timestamps. Character 1 (the first entry, "primary") is the
 * account's own UUID when the registry is first created, so a world's existing progress becomes
 * character 1 without moving any file.
 * <p>
 * Format (version 1):
 * <pre>
 * {
 *   "version": 1,
 *   "characters": [ {"uuid": "…", "name": "Steve", "created": 1761480000000, "lastPlayed": 1761480000000, "skin": "alex", "arms": "slim"}, … ],
 *   "retired": [ "…" ]   // UUIDs of deleted characters, so their leftovers are never mistaken for legacy owner data
 * }
 * </pre>
 * "skin" (one of vanilla's nine default skins) and "arms" ("wide" or "slim") are optional: a
 * character without them wears the account's skin.
 * Timestamps are epoch milliseconds; {@code lastPlayed} 0 means "never played". The file is read
 * and written on the client (before the integrated server starts, and when a session ends), never
 * by the server, so there is no cross-thread access to it.
 */
public final class CharacterRegistry {

    public static final String FILE_NAME = "terrainlab_characters.json";
    public static final int MAX_CHARACTERS = 4;
    public static final int MAX_NAME_LENGTH = 16;
    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * One character. {@code name} is display-only; the game identifies the character by {@code uuid}.
     * {@code skin} is the name of a default skin ("steve", "alex", ...), or null for the account's skin.
     */
    public record Entry(UUID uuid, String name, long created, long lastPlayed, String skin, boolean slim) {
        public Entry(UUID uuid, String name, long created, long lastPlayed) {
            this(uuid, name, created, lastPlayed, null, false);
        }

        public Entry withName(String newName) {
            return new Entry(this.uuid, newName, this.created, this.lastPlayed, this.skin, this.slim);
        }

        public Entry withLastPlayed(long time) {
            return new Entry(this.uuid, this.name, this.created, time, this.skin, this.slim);
        }

        public Entry withSkin(String newSkin, boolean newSlim) {
            return new Entry(this.uuid, this.name, this.created, this.lastPlayed, newSkin, newSlim);
        }
    }

    private final Path worldDir;
    private final List<Entry> characters = new ArrayList<>();
    private final Set<UUID> retired = new LinkedHashSet<>();

    private CharacterRegistry(Path worldDir) {
        this.worldDir = worldDir;
    }

    public Path worldDir() {
        return this.worldDir;
    }

    public static Path file(Path worldDir) {
        return worldDir.resolve(FILE_NAME);
    }

    /**
     * Reads the registry of a world, creating it in memory (not on disk) when the file is missing or
     * unreadable. A new registry holds one character: the world's existing progress, under
     * {@code primaryUuid} (the account's UUID) and {@code primaryName}.
     * A corrupt file is kept aside as {@code terrainlab_characters.json.corrupt} so nothing is lost.
     */
    public static CharacterRegistry loadOrCreate(Path worldDir, UUID primaryUuid, String primaryName, long primaryLastPlayed) {
        CharacterRegistry registry = new CharacterRegistry(worldDir);
        Path file = file(worldDir);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                registry.read(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                TerrainLab.LOGGER.warn("Unreadable character list {}, starting a new one", file, e);
                registry.characters.clear();
                registry.retired.clear();
                try {
                    Files.copy(file, worldDir.resolve(FILE_NAME + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }
        if (registry.characters.isEmpty()) {
            registry.characters.add(new Entry(primaryUuid, sanitizeName(primaryName, "Character 1"), System.currentTimeMillis(), primaryLastPlayed));
        }
        return registry;
    }

    /** Reads an existing registry without creating one; null when the world has none or it is unreadable. */
    public static CharacterRegistry loadExisting(Path worldDir) {
        Path file = file(worldDir);
        if (!Files.isRegularFile(file)) return null;
        CharacterRegistry registry = new CharacterRegistry(worldDir);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            registry.read(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            TerrainLab.LOGGER.warn("Unreadable character list {}", file, e);
            return null;
        }
        return registry.characters.isEmpty() ? null : registry;
    }

    private void read(JsonObject root) {
        JsonArray list = root.has("characters") ? root.getAsJsonArray("characters") : new JsonArray();
        Set<UUID> seen = new LinkedHashSet<>();
        for (JsonElement element : list) {
            try {
                JsonObject o = element.getAsJsonObject();
                UUID uuid = UUID.fromString(o.get("uuid").getAsString());
                if (!seen.add(uuid) || this.characters.size() >= MAX_CHARACTERS) continue;
                String name = sanitizeName(o.has("name") ? o.get("name").getAsString() : "", "Character " + (this.characters.size() + 1));
                long created = o.has("created") ? o.get("created").getAsLong() : 0L;
                long lastPlayed = o.has("lastPlayed") ? o.get("lastPlayed").getAsLong() : 0L;
                String skin = o.has("skin") ? o.get("skin").getAsString() : null;
                boolean slim = o.has("arms") && "slim".equals(o.get("arms").getAsString());
                this.characters.add(new Entry(uuid, name, created, lastPlayed, skin, slim));
            } catch (Exception e) {
                TerrainLab.LOGGER.warn("Skipping malformed character entry {}", element);
            }
        }
        if (root.has("retired")) {
            for (JsonElement element : root.getAsJsonArray("retired")) {
                try {
                    this.retired.add(UUID.fromString(element.getAsString()));
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Writes the registry atomically (temp file + move), so a crash never leaves a half-written list. */
    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray list = new JsonArray();
        for (Entry entry : this.characters) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", entry.uuid().toString());
            o.addProperty("name", entry.name());
            o.addProperty("created", entry.created());
            o.addProperty("lastPlayed", entry.lastPlayed());
            if (entry.skin() != null) {
                o.addProperty("skin", entry.skin());
                o.addProperty("arms", entry.slim() ? "slim" : "wide");
            }
            list.add(o);
        }
        root.add("characters", list);
        JsonArray retiredList = new JsonArray();
        for (UUID uuid : this.retired) retiredList.add(uuid.toString());
        root.add("retired", retiredList);

        Path file = file(this.worldDir);
        try {
            Path tmp = Files.createTempFile(this.worldDir, "terrainlab_characters", ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            TerrainLab.LOGGER.error("Failed to save character list {}", file, e);
        }
    }

    public List<Entry> characters() {
        return Collections.unmodifiableList(this.characters);
    }

    public Entry primary() {
        return this.characters.getFirst();
    }

    public Entry get(UUID uuid) {
        for (Entry entry : this.characters) {
            if (entry.uuid().equals(uuid)) return entry;
        }
        return null;
    }

    public boolean canAdd() {
        return this.characters.size() < MAX_CHARACTERS;
    }

    /** Every UUID this world has ever used for a character (current and deleted). */
    public Set<UUID> knownUuids() {
        Set<UUID> known = new LinkedHashSet<>(this.retired);
        for (Entry entry : this.characters) known.add(entry.uuid());
        return known;
    }

    /** Adds a fresh character with a new random UUID; it has no player files yet, so it starts like a new player. */
    public Entry add(String name) {
        if (!this.canAdd()) throw new IllegalStateException("A world can hold at most " + MAX_CHARACTERS + " characters");
        UUID uuid;
        do {
            uuid = UUID.randomUUID();
        } while (this.knownUuids().contains(uuid));
        Entry entry = new Entry(uuid, sanitizeName(name, "Character " + (this.characters.size() + 1)), System.currentTimeMillis(), 0L);
        this.characters.add(entry);
        return entry;
    }

    public void replace(Entry updated) {
        for (int i = 0; i < this.characters.size(); i++) {
            if (this.characters.get(i).uuid().equals(updated.uuid())) {
                this.characters.set(i, updated);
                return;
            }
        }
    }

    /** Removes a character from the list (never the last one) and remembers its UUID as retired. */
    public boolean remove(UUID uuid) {
        if (this.characters.size() <= 1) return false;
        boolean removed = this.characters.removeIf(e -> e.uuid().equals(uuid));
        if (removed) this.retired.add(uuid);
        return removed;
    }

    public boolean isNameTaken(String name, UUID except) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        for (Entry entry : this.characters) {
            if (!entry.uuid().equals(except) && entry.name().toLowerCase(Locale.ROOT).equals(wanted)) return true;
        }
        return false;
    }

    /** Trims, strips formatting codes and control characters, and caps at {@link #MAX_NAME_LENGTH}. */
    public static String sanitizeName(String name, String fallback) {
        StringBuilder sb = new StringBuilder();
        if (name != null) {
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == '§') {
                    i++;
                    continue;
                }
                if (c >= ' ' && c != 127) sb.append(c);
            }
        }
        String result = sb.toString().trim();
        if (result.length() > MAX_NAME_LENGTH) result = result.substring(0, MAX_NAME_LENGTH).trim();
        return result.isEmpty() ? fallback : result;
    }
}
