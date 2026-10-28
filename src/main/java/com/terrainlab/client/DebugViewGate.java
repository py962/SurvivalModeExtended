package com.terrainlab.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;

import java.util.Set;

/**
 * F3 world overlays (chunk borders, hitboxes and the other visualisers) only work for players who can
 * use cheat commands: cheats enabled in singleplayer, or operator status on a server.
 */
public final class DebugViewGate {
    public static final Set<Identifier> CHEAT_ONLY = Set.of(
            DebugScreenEntries.ENTITY_HITBOXES,
            DebugScreenEntries.CHUNK_BORDERS,
            DebugScreenEntries.CHUNK_SECTION_PATHS,
            DebugScreenEntries.CHUNK_SECTION_OCTREE,
            DebugScreenEntries.CHUNK_SECTION_VISIBILITY,
            DebugScreenEntries.VISUALIZE_WATER_LEVELS,
            DebugScreenEntries.VISUALIZE_HEIGHTMAP,
            DebugScreenEntries.VISUALIZE_COLLISION_BOXES,
            DebugScreenEntries.VISUALIZE_ENTITY_SUPPORTING_BLOCKS,
            DebugScreenEntries.VISUALIZE_BLOCK_LIGHT_LEVELS,
            DebugScreenEntries.VISUALIZE_SKY_LIGHT_LEVELS,
            DebugScreenEntries.VISUALIZE_SOLID_FACES,
            DebugScreenEntries.VISUALIZE_CHUNKS_ON_SERVER,
            DebugScreenEntries.VISUALIZE_SKY_LIGHT_SECTIONS);

    private DebugViewGate() {}

    /** True if {@code id} is a cheat-only overlay and the local player cannot use cheats. */
    public static boolean blocked(Identifier id) {
        if (!CHEAT_ONLY.contains(id)) return false;
        var player = Minecraft.getInstance().player;
        return player == null || !player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }
}
