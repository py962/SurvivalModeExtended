package com.terrainlab.item;

import com.terrainlab.TerrainLab;
import com.terrainlab.registry.ModMapDecorations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Marks structures seen through a spyglass on the scoped map, using vanilla's icon where one exists
 * and TerrainLab's own otherwise. Only structures that reach the surface are marked; anything whose
 * top lies below the ground (buried treasure, strongholds, mineshafts, buried ruins) cannot be seen.
 * Markers are stored on the map item, like an explorer map's target, without tinting the item.
 */
public final class SpyglassStructureMarker {

    // How far below the ground (or sea floor) a structure's top may sit and still count as visible.
    private static final int VISIBLE_DEPTH = 2; // blocks

    private static final Map<String, Supplier<Holder<MapDecorationType>>> ICONS = Map.ofEntries(
            Map.entry("minecraft:village_plains", () -> MapDecorationTypes.PLAINS_VILLAGE),
            Map.entry("minecraft:village_desert", () -> MapDecorationTypes.DESERT_VILLAGE),
            Map.entry("minecraft:village_savanna", () -> MapDecorationTypes.SAVANNA_VILLAGE),
            Map.entry("minecraft:village_snowy", () -> MapDecorationTypes.SNOWY_VILLAGE),
            Map.entry("minecraft:village_taiga", () -> MapDecorationTypes.TAIGA_VILLAGE),
            Map.entry("minecraft:mansion", () -> MapDecorationTypes.WOODLAND_MANSION),
            Map.entry("minecraft:monument", () -> MapDecorationTypes.OCEAN_MONUMENT),
            Map.entry("minecraft:jungle_pyramid", () -> MapDecorationTypes.JUNGLE_TEMPLE),
            Map.entry("minecraft:swamp_hut", () -> MapDecorationTypes.SWAMP_HUT),
            Map.entry("minecraft:trial_chambers", () -> MapDecorationTypes.TRIAL_CHAMBERS),
            Map.entry("minecraft:pillager_outpost", ModMapDecorations.PILLAGER_OUTPOST::getDelegate),
            Map.entry("minecraft:desert_pyramid", ModMapDecorations.DESERT_PYRAMID::getDelegate),
            Map.entry("minecraft:igloo", ModMapDecorations.IGLOO::getDelegate),
            Map.entry("minecraft:shipwreck", ModMapDecorations.SHIPWRECK::getDelegate),
            Map.entry("minecraft:shipwreck_beached", ModMapDecorations.SHIPWRECK::getDelegate),
            Map.entry("minecraft:ruined_portal", ModMapDecorations.RUINED_PORTAL::getDelegate),
            Map.entry("minecraft:ruined_portal_desert", ModMapDecorations.RUINED_PORTAL::getDelegate),
            Map.entry("minecraft:ruined_portal_jungle", ModMapDecorations.RUINED_PORTAL::getDelegate),
            Map.entry("minecraft:ruined_portal_swamp", ModMapDecorations.RUINED_PORTAL::getDelegate),
            Map.entry("minecraft:ruined_portal_mountain", ModMapDecorations.RUINED_PORTAL::getDelegate),
            Map.entry("minecraft:ruined_portal_ocean", ModMapDecorations.RUINED_PORTAL::getDelegate),
            Map.entry("minecraft:ocean_ruin_cold", ModMapDecorations.OCEAN_RUIN::getDelegate),
            Map.entry("minecraft:ocean_ruin_warm", ModMapDecorations.OCEAN_RUIN::getDelegate),
            Map.entry("minecraft:trail_ruins", ModMapDecorations.TRAIL_RUINS::getDelegate),
            Map.entry("minecraft:end_city", ModMapDecorations.END_CITY::getDelegate),
            Map.entry(TerrainLab.MODID + ":outlaws_hideout", ModMapDecorations.OUTLAWS_HIDEOUT::getDelegate),
            Map.entry(TerrainLab.MODID + ":trappers_lodge", ModMapDecorations.TRAPPERS_LODGE::getDelegate),
            Map.entry(TerrainLab.MODID + ":pirates_cove", ModMapDecorations.PIRATES_COVE::getDelegate));

    private SpyglassStructureMarker() {
    }

    /** Adds a marker for each visible structure with a piece in {@code chunk} that lies on the map. */
    public static void markChunk(ServerLevel level, ItemStack map, MapItemSavedData data, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x(), chunk.z())) {
            return;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (StructureStart start : level.structureManager().startsForStructure(chunk.x(), chunk.z(), s -> true)) {
            if (!start.isValid()) {
                continue;
            }
            Structure structure = start.getStructure();
            Identifier id = registry.getKey(structure);
            Supplier<Holder<MapDecorationType>> icon = id == null ? null : ICONS.get(id.toString());
            if (icon == null) {
                continue;
            }
            BoundingBox box = start.getBoundingBox();
            BlockPos centre = box.getCenter();
            if (!isOnMap(data, centre) || !isVisible(level, box, centre)) {
                continue;
            }
            String key = TerrainLab.MODID + "_structure_" + centre.getX() + "_" + centre.getZ();
            MapDecorations.Entry entry = new MapDecorations.Entry(icon.get(), centre.getX(), centre.getZ(), 180.0F);
            map.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY,
                    decorations -> decorations.decorations().containsKey(key) ? decorations : decorations.withDecoration(key, entry));
        }
    }

    private static boolean isVisible(ServerLevel level, BoundingBox box, BlockPos centre) {
        int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR, centre.getX(), centre.getZ());
        return box.maxY() >= ground - VISIBLE_DEPTH;
    }

    private static boolean isOnMap(MapItemSavedData data, BlockPos pos) {
        int scale = 1 << data.scale;
        int px = Mth.floor((double) (pos.getX() - data.centerX) / scale) + 64;
        int pz = Mth.floor((double) (pos.getZ() - data.centerZ) / scale) + 64;
        return px >= 0 && pz >= 0 && px < 128 && pz < 128;
    }
}
