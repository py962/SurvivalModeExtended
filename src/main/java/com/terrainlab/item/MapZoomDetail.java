package com.terrainlab.item;

import com.terrainlab.mixin.MapItemSavedDataBannersAccessor;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.Map;

/** Carries an old map's explored area and banners onto its zoomed-out copy. */
public final class MapZoomDetail {

    private static final int SIZE = 128;
    private static final int HALF = 64;

    private MapZoomDetail() {
    }

    public static void copy(MapItemSavedData from, MapItemSavedData to) {
        if (!from.dimension.equals(to.dimension) || to.scale < from.scale) {
            return;
        }
        int oldStep = 1 << from.scale;
        int newStep = 1 << to.scale;
        int ratio = newStep / oldStep;
        int oldOriginX = (from.centerX / oldStep - HALF) * oldStep;
        int oldOriginZ = (from.centerZ / oldStep - HALF) * oldStep;
        boolean changed = false;

        for (int v = 0; v < SIZE; v++) {
            int blockZ = (to.centerZ / newStep + v - HALF) * newStep;
            for (int u = 0; u < SIZE; u++) {
                int blockX = (to.centerX / newStep + u - HALF) * newStep;
                byte colour = sample(from, blockX, blockZ, oldOriginX, oldOriginZ, oldStep, ratio);
                if (colour != 0) {
                    to.colors[u + v * SIZE] = colour;
                    changed = true;
                }
            }
        }

        Map<String, MapBanner> banners = ((MapItemSavedDataBannersAccessor) to).terrainlab$bannerMarkers();
        for (MapBanner banner : from.getBanners()) {
            if (inside(to, banner.pos().getX(), banner.pos().getZ())) {
                banners.put(banner.getId(), banner);
                to.addDecoration(banner.getDecoration(), null, banner.getId(),
                        banner.pos().getX() + 0.5, banner.pos().getZ() + 0.5, 180.0, banner.name().orElse(null));
                changed = true;
            }
        }

        if (changed) {
            to.setDirty();
        }
    }

    /** Most common explored colour among the old pixels that fall inside one new pixel. */
    private static byte sample(MapItemSavedData from, int blockX, int blockZ,
                               int oldOriginX, int oldOriginZ, int oldStep, int ratio) {
        byte best = 0;
        int bestCount = 0;
        byte[] seen = new byte[ratio * ratio];
        int seenCount = 0;
        for (int dz = 0; dz < ratio; dz++) {
            int oz = Math.floorDiv(blockZ + dz * oldStep - oldOriginZ, oldStep);
            if (oz < 0 || oz >= SIZE) {
                continue;
            }
            for (int dx = 0; dx < ratio; dx++) {
                int ox = Math.floorDiv(blockX + dx * oldStep - oldOriginX, oldStep);
                if (ox < 0 || ox >= SIZE) {
                    continue;
                }
                byte colour = from.colors[ox + oz * SIZE];
                if (colour != 0) {
                    seen[seenCount++] = colour;
                }
            }
        }
        for (int i = 0; i < seenCount; i++) {
            int count = 0;
            for (int j = 0; j < seenCount; j++) {
                if (seen[j] == seen[i]) {
                    count++;
                }
            }
            if (count > bestCount) {
                best = seen[i];
                bestCount = count;
            }
        }
        return best;
    }

    private static boolean inside(MapItemSavedData map, int x, int z) {
        double half = HALF * (1 << map.scale) - 0.5;
        return Math.abs(x + 0.5 - map.centerX) <= half && Math.abs(z + 0.5 - map.centerZ) <= half;
    }
}
