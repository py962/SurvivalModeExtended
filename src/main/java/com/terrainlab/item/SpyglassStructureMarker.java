package com.terrainlab.item;

import com.terrainlab.TerrainLab;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Marks structures seen through a spyglass on the scoped map, with the icons from
 * {@link StructureMapIcons} (vanilla's where one exists, TerrainLab's own otherwise). Only structures
 * with some part above the ground or the water surface are marked: buried and fully submerged ones
 * (strongholds, sunken ruins and shipwrecks, ocean monuments) cannot be seen, and buried treasure is
 * never marked.
 * Markers are stored on the map item, like an explorer map's target, without tinting the item.
 */
public final class SpyglassStructureMarker {

    // Footprint columns sampled per axis when looking for a part that breaks the surface.
    private static final int SAMPLES_PER_AXIS = 5;

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
            StructureMapIcons.Icon icon = StructureMapIcons.forStructure(id);
            if (icon == null || !icon.spyglassMarked()) {
                continue;
            }
            BoundingBox box = start.getBoundingBox();
            BlockPos centre = box.getCenter();
            if (!isOnMap(data, centre) || !isVisible(level, box, centre)) {
                continue;
            }
            String key = TerrainLab.MODID + "_structure_" + centre.getX() + "_" + centre.getZ();
            MapDecorations.Entry entry = new MapDecorations.Entry(icon.decoration().get(), centre.getX(), centre.getZ(), 180.0F);
            map.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY,
                    decorations -> decorations.decorations().containsKey(key) ? decorations : decorations.withDecoration(key, entry));
        }
    }

    // The heightmap counts water and the structure's own blocks but not leaves, so a column whose
    // surface is no higher than the structure's top has the structure itself showing there.
    private static boolean isVisible(ServerLevel level, BoundingBox box, BlockPos centre) {
        for (int i = 0; i < SAMPLES_PER_AXIS; i++) {
            int x = box.minX() + (box.getXSpan() - 1) * i / (SAMPLES_PER_AXIS - 1);
            for (int j = 0; j < SAMPLES_PER_AXIS; j++) {
                int z = box.minZ() + (box.getZSpan() - 1) * j / (SAMPLES_PER_AXIS - 1);
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (box.maxY() >= surface - 1) return true;
            }
        }
        return false;
    }

    private static boolean isOnMap(MapItemSavedData data, BlockPos pos) {
        int scale = 1 << data.scale;
        int px = Mth.floor((double) (pos.getX() - data.centerX) / scale) + 64;
        int pz = Mth.floor((double) (pos.getZ() - data.centerZ) / scale) + 64;
        return px >= 0 && pz >= 0 && px < 128 && pz < 128;
    }
}
