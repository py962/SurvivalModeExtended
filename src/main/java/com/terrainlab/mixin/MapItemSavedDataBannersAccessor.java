package com.terrainlab.mixin;

import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataBannersAccessor {

    @Accessor("bannerMarkers")
    Map<String, MapBanner> terrainlab$bannerMarkers();
}
