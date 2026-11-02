package com.terrainlab.character;

import com.terrainlab.TerrainLab;
import com.terrainlab.entity.AbstractTrailTraderEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Set;
import java.util.UUID;

/**
 * Game-bus hooks for singleplayer characters.
 * <p>
 * A character keeps the account's profile NAME (so the singleplayer owner checks, which compare
 * names, keep granting the host its owner/cheat permissions) but has its own UUID. The character's
 * chosen name is shown instead wherever the game uses the display name: chat, death and join
 * messages, the name tag ({@link PlayerEvent.NameFormat}) and the tab list
 * ({@link PlayerEvent.TabListNameFormat}). Both events fire for the player objects of either side
 * of this JVM; only the player whose UUID is the active character is renamed, so LAN guests are
 * untouched. Outside a character session {@link CharacterSession#active()} is null and nothing changes.
 */
@EventBusSubscriber(modid = TerrainLab.MODID)
public final class CharacterServerEvents {

    private CharacterServerEvents() {
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        CharacterSession.Selection active = CharacterSession.active();
        if (active != null && event.getEntity().getUUID().equals(active.uuid())) {
            event.setDisplayname(Component.literal(active.name()));
        }
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        CharacterSession.Selection active = CharacterSession.active();
        if (active != null && event.getEntity().getUUID().equals(active.uuid())) {
            event.setDisplayName(Component.literal(active.name()));
        }
    }

    /** Frees the guide reservations of characters deleted since the world was last played. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Set<UUID> retired = CharacterSession.retiredFor(event.getServer());
        if (retired.isEmpty()) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            AbstractTrailTraderEntity.releaseRetiredReservations(level, retired);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CharacterSession.release(event.getServer());
    }
}
