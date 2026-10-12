package com.terrainlab.client.item.properties;

import com.mojang.serialization.MapCodec;
import com.terrainlab.temperature.BubbleGaugeDial;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Reports the holder's raw Y-coordinate via BubbleGaugeDial. Same
 * held-or-framed generalization as Calendar/Thermometer - ItemOwner
 * works identically whether the item is held or mounted in a frame, no
 * special-casing needed.
 */
public class BubbleGaugeReading implements RangeSelectItemModelProperty {

    public static final MapCodec<BubbleGaugeReading> MAP_CODEC =
            MapCodec.unit(BubbleGaugeReading::new);

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (level == null || owner == null) {
            return 0.0F;
        }
        Vec3 pos = owner.position();
        return BubbleGaugeDial.getFraction(pos.y());
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }
}
