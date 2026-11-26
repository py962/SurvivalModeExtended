package com.terrainlab.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.terrainlab.item.MapZoomDetail;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A zoomed-out map keeps what the original had already explored, instead of starting blank. */
@Mixin(MapItem.class)
public abstract class MapItemZoomDetailMixin {

    @WrapOperation(method = "scaleMap",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setMapData(Lnet/minecraft/world/level/saveddata/maps/MapId;Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;)V"),
            require = 1)
    private static void terrainlab$keepExploredArea(ServerLevel level, MapId id, MapItemSavedData zoomed,
                                                    Operation<Void> original,
                                                    @Local(ordinal = 0) MapItemSavedData source) {
        if (source != zoomed) {
            MapZoomDetail.copy(source, zoomed);
        }
        original.call(level, id, zoomed);
    }
}
