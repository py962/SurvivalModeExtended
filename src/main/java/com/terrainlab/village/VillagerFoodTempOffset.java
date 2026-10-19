package com.terrainlab.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A timed temperature offset (°C) from warming or cooling food eaten out of the village chest.
 * Added to the villager's combined temperature in {@link VillagerThermalService}; it decays linearly
 * from {@code offset} at {@code startTick} to 0 at {@code endTick} (game time).
 */
public record VillagerFoodTempOffset(float offset, long startTick, long endTick) {

    public static final VillagerFoodTempOffset NONE = new VillagerFoodTempOffset(0.0F, 0L, 0L);

    public static final MapCodec<VillagerFoodTempOffset> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("offset").forGetter(VillagerFoodTempOffset::offset),
            Codec.LONG.fieldOf("start").forGetter(VillagerFoodTempOffset::startTick),
            Codec.LONG.fieldOf("end").forGetter(VillagerFoodTempOffset::endTick)
    ).apply(i, VillagerFoodTempOffset::new));

    /** The offset still in effect at {@code now}, or 0 once expired. */
    public double currentAt(long now) {
        if (offset == 0.0F || now >= endTick || endTick <= startTick) {
            return 0.0;
        }
        if (now <= startTick) {
            return offset;
        }
        return offset * (double) (endTick - now) / (double) (endTick - startTick);
    }
}
