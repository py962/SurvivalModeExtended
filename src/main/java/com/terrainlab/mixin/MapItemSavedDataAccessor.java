package com.terrainlab.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MapItemSavedData.class)
public interface MapItemSavedDataAccessor {
    @Accessor("trackingPosition")
    boolean terrainlab$isTrackingPosition();

    @Accessor("unlimitedTracking")
    boolean terrainlab$isUnlimitedTracking();

    @Invoker("<init>")
    static MapItemSavedData terrainlab$create(int centerX, int centerZ, byte scale, boolean trackingPosition,
                                              boolean unlimitedTracking, boolean locked, ResourceKey<Level> dimension) {
        throw new AssertionError();
    }
}
