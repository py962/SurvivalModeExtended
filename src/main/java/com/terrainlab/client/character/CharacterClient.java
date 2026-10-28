package com.terrainlab.client.character;

import com.mojang.authlib.GameProfile;
import com.terrainlab.TerrainLab;
import com.terrainlab.character.CharacterRegistry;
import com.terrainlab.character.CharacterSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client side of singleplayer characters: opens the character screen from the world list, turns
 * the chosen character into a {@link CharacterSession.Selection} and continues the vanilla world
 * open, and keeps each character's "last played" time.
 */
@EventBusSubscriber(modid = TerrainLab.MODID, value = Dist.CLIENT)
public final class CharacterClient {

    /** True while {@link #continueJoin} re-enters {@code WorldListEntry#joinWorld}, so the hook lets it through. */
    private static boolean continuingJoin;
    /** The character being played in the current singleplayer session (client thread only). */
    private static CharacterSession.Selection current;

    private CharacterClient() {
    }

    public static boolean isContinuingJoin() {
        return continuingJoin;
    }

    /** Opens the character screen for an existing world; false (vanilla open) if the folder cannot be resolved. */
    public static boolean openCharacterScreen(WorldSelectionList.WorldListEntry entry, LevelSummary summary, Runnable back) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Path worldDir = mc.getLevelSource().getLevelPath(summary.getLevelId()).toAbsolutePath().normalize();
            if (!Files.isDirectory(worldDir)) return false;
            CharacterSession.clearPending();
            mc.gui.setScreen(new CharacterSelectScreen(entry, summary, worldDir, back));
            return true;
        } catch (Exception e) {
            TerrainLab.LOGGER.error("Could not open the character screen for {}", summary.getLevelId(), e);
            return false;
        }
    }

    /**
     * Plays {@code character}: records it as the pending selection for this world, then runs the
     * world list entry's normal {@code joinWorld}, which continues into the vanilla open flow.
     * <p>
     * The profile handed to the integrated server keeps the account's NAME (the singleplayer
     * owner checks compare names: owner permissions, cheats, pause, no idle kick, "name taken"
     * guard) and its properties (the signed skin/cape textures, which carry no UUID check, so the
     * account's skin renders), with the character's UUID. Character 1 normally IS the account's
     * UUID, in which case the account's profile is used as is.
     */
    public static void continueJoin(WorldSelectionList.WorldListEntry entry, CharacterRegistry registry, CharacterRegistry.Entry character) {
        GameProfile account = Minecraft.getInstance().getGameProfile();
        GameProfile profile = account.id().equals(character.uuid())
                ? account
                : new GameProfile(character.uuid(), account.name(), account.properties());
        CharacterSession.select(new CharacterSession.Selection(
                registry.worldDir(), character.uuid(), character.name(), registry.primary().uuid(), registry.knownUuids(), profile));
        continuingJoin = true;
        try {
            entry.joinWorld();
        } finally {
            continuingJoin = false;
        }
    }

    /** Called from the integrated server constructor once the selection is bound: the world is opening. */
    public static void onCharacterSessionStarted(CharacterSession.Selection character) {
        current = character;
        touchLastPlayed(character);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CharacterSession.Selection character = current;
        current = null;
        if (character != null) touchLastPlayed(character);
    }

    private static void touchLastPlayed(CharacterSession.Selection character) {
        try {
            CharacterRegistry registry = CharacterRegistry.loadExisting(character.worldDir());
            if (registry == null) return;
            CharacterRegistry.Entry entry = registry.get(character.uuid());
            if (entry == null) return;
            registry.replace(entry.withLastPlayed(System.currentTimeMillis()));
            registry.save();
        } catch (Exception e) {
            TerrainLab.LOGGER.warn("Could not update the character list of {}", character.worldDir(), e);
        }
    }
}
