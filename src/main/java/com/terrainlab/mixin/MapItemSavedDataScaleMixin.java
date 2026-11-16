package com.terrainlab.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapFrame;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.List;

/** Lets map data hold scale 5, both when zoomed and when loaded from disk. */
@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataScaleMixin {

    @Shadow
    @Final
    @Mutable
    public byte scale;

    @ModifyConstant(method = "scaled", constant = @Constant(intValue = 4), require = 1)
    private int terrainlab$maxScale(int vanillaMax) {
        return 5;
    }

    // The loading constructor clamps to 4 before delegating, where injectors can't reach; undo it after.
    @Inject(method = "<init>(Lnet/minecraft/resources/ResourceKey;IIBLjava/nio/ByteBuffer;ZZZLjava/util/List;Ljava/util/List;)V",
            at = @At("RETURN"), require = 1)
    private void terrainlab$keepLoadedScale(ResourceKey<Level> dimension, int centerX, int centerZ, byte loadedScale,
                                            ByteBuffer colors, boolean trackingPosition, boolean unlimitedTracking,
                                            boolean locked, List<MapBanner> banners, List<MapFrame> frames, CallbackInfo ci) {
        if (loadedScale > this.scale) {
            this.scale = (byte) Math.min(loadedScale, 5);
        }
    }
}
