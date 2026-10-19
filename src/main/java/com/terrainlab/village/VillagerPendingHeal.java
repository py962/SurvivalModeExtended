package com.terrainlab.village;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A villager's queued heal-over-time from food eaten out of the village chest: {@code amount} HP
 * still to deliver, spread evenly over the remaining {@code ticks}. Delivered in steps on the
 * staggered villager read (see {@link VillagerHealthService}).
 */
public record VillagerPendingHeal(float amount, int ticks) {

    public static final VillagerPendingHeal NONE = new VillagerPendingHeal(0.0F, 0);

    public static final MapCodec<VillagerPendingHeal> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.FLOAT.fieldOf("amount").forGetter(VillagerPendingHeal::amount),
            com.mojang.serialization.Codec.INT.fieldOf("ticks").forGetter(VillagerPendingHeal::ticks)
    ).apply(i, VillagerPendingHeal::new));

    public boolean isEmpty() {
        return amount <= 0.0F;
    }
}
