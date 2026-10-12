package com.terrainlab.client.item.properties;

import com.mojang.serialization.MapCodec;
import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.season.CalendarDial;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class SeasonAngle implements RangeSelectItemModelProperty {

    public static final MapCodec<SeasonAngle> MAP_CODEC =
            MapCodec.unit(SeasonAngle::new);

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (level == null) {
            return 0.0F;
        }
        long gameTime = level.getGameTime();
        long seasonalOffset = ClientSeasonState.getSeasonalOffset();
        return CalendarDial.getNeedleRotation(gameTime, seasonalOffset);
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }
}