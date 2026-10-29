package com.terrainlab.character;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Hand-over of the chosen character from the client's character screen to the integrated server
 * of the same JVM, and the per-server context the server-side hooks read.
 * <p>
 * Flow: the character screen calls {@link #select} just before continuing the vanilla world-open
 * flow; the integrated server's constructor ({@code IntegratedServerCharacterMixin}) calls
 * {@link #claim}, which binds the selection to that server instance only if the server is opening
 * the same world folder. From then on {@link #forServer} gives the server hooks (player data load,
 * level.dat owner UUID, display name) the active character; everything else (dedicated servers,
 * new worlds, demo/quick-play worlds, multiplayer) sees null and behaves exactly as vanilla.
 * The binding is dropped when that server stops or the client logs out.
 * <p>
 * Lives in common code because the server-side hooks (PlayerList, MinecraftServer) are common
 * classes; it references no client class.
 */
public final class CharacterSession {

    /**
     * The chosen character for one world.
     *
     * @param worldDir  the world folder (absolute, normalized)
     * @param uuid      the character's player UUID (the UUID the game knows it by)
     * @param name      the character's display name (chat, tab list, name tag)
     * @param primaryUuid the UUID of character 1 (the world's original progress)
     * @param known     every UUID this world has used for a character, current and deleted
     * @param profile   the profile the integrated server should log the local player in with:
     *                  the account's NAME and skin properties, the character's UUID
     */
    public record Selection(Path worldDir, UUID uuid, String name, UUID primaryUuid, Set<UUID> known, GameProfile profile) {
        /** Whether this is character 1. */
        public boolean primary() {
            return this.uuid.equals(this.primaryUuid);
        }
    }

    private record Bound(WeakReference<MinecraftServer> server, Selection selection) {
    }

    /**
     * The first character of a world being created: chosen on the naming screen before the world
     * exists, turned into the world's character list when its integrated server starts.
     *
     * @param profile the account's profile (character 1 always has the account's UUID)
     */
    public record NewWorldCharacter(String name, String skin, boolean slim, GameProfile profile) {
    }

    private static volatile NewWorldCharacter pendingNewWorld;

    private static volatile Selection pending;
    private static volatile Bound bound;

    private CharacterSession() {
    }

    /** Client: remembers the character chosen for the world about to be opened. */
    public static void select(Selection selection) {
        pending = selection;
    }

    /** Client: remembers the first character for the world about to be created. */
    public static void selectForNewWorld(NewWorldCharacter character) {
        pendingNewWorld = character;
    }

    /** Client: forgets a selection that was not used (the character screen was reopened or left). */
    public static void clearPending() {
        pending = null;
    }

    /**
     * Integrated server constructor: takes the pending selection if it is for this server's world
     * folder and binds it to this server. Always consumes the pending selection, so a selection
     * whose open was cancelled can never leak into a different world (e.g. a brand-new one).
     */
    public static Selection claim(MinecraftServer server) {
        Selection selection = pending;
        pending = null;
        NewWorldCharacter fresh = pendingNewWorld;
        pendingNewWorld = null;
        if (selection == null && fresh != null) {
            selection = createFirstCharacter(server, fresh);
        }
        if (selection == null) {
            bound = null;
            return null;
        }
        Path serverWorld = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!serverWorld.equals(selection.worldDir())) {
            bound = null;
            return null;
        }
        bound = new Bound(new WeakReference<>(server), selection);
        return selection;
    }

    /**
     * Writes the character list of a newly created world with its first character, as chosen on the
     * naming screen. Does nothing (null) for a world that already has a list, so a stale choice can
     * never rename an existing world's character.
     */
    private static Selection createFirstCharacter(MinecraftServer server, NewWorldCharacter fresh) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (Files.exists(CharacterRegistry.file(worldDir))) return null;
        CharacterRegistry registry = CharacterRegistry.loadOrCreate(worldDir, fresh.profile().id(), fresh.name(), 0L);
        CharacterRegistry.Entry first = registry.primary().withSkin(fresh.skin(), fresh.slim());
        registry.replace(first);
        registry.save();
        return new Selection(worldDir, first.uuid(), first.name(), first.uuid(), registry.knownUuids(), fresh.profile());
    }

    /** The character active on this server, or null (vanilla behaviour). */
    public static Selection forServer(MinecraftServer server) {
        Bound b = bound;
        return b != null && server != null && b.server().get() == server ? b.selection() : null;
    }

    /** The character active in this JVM's integrated server, or null. Used by display-name hooks on either side. */
    public static Selection active() {
        Bound b = bound;
        if (b == null) return null;
        MinecraftServer server = b.server().get();
        return server != null && server.isRunning() ? b.selection() : null;
    }

    /** Drops the binding when that server stops (or unconditionally with null). */
    public static void release(MinecraftServer server) {
        Bound b = bound;
        if (b != null && (server == null || b.server().get() == server)) {
            bound = null;
        }
    }
}
