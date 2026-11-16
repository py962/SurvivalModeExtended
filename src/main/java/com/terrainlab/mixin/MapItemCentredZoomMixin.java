package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.terrainlab.item.MapZoomCrafter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A zoomed-out map is centred on the player taking it from the cartography table, instead of
 * snapping to vanilla's world grid. Away from the map's dimension, or with no player (a crafter),
 * it stays centred on the original map.
 */
@Mixin(MapItem.class)
public abstract class MapItemCentredZoomMixin {

    @WrapOperation(method = "scaleMap",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;scaled()Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;"),
            require = 1)
    private static MapItemSavedData terrainlab$centredZoom(MapItemSavedData original, Operation<MapItemSavedData> scaled) {
        if (original.scale >= 5) {
            return scaled.call(original);
        }
        int centerX = original.centerX;
        int centerZ = original.centerZ;
        Player player = MapZoomCrafter.get();
        if (player != null && player.level().dimension().equals(original.dimension)) {
            centerX = Mth.floor(player.getX());
            centerZ = Mth.floor(player.getZ());
        }
        MapItemSavedDataAccessor data = (MapItemSavedDataAccessor) original;
        return MapItemSavedDataAccessor.terrainlab$create(centerX, centerZ, (byte) (original.scale + 1),
                data.terrainlab$isTrackingPosition(), data.terrainlab$isUnlimitedTracking(), false, original.dimension);
    }
}
