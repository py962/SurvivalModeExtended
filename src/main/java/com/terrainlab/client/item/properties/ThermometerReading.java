package com.terrainlab.client.item.properties;

import com.mojang.serialization.MapCodec;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.temperature.LocalTemperatureService;
import com.terrainlab.temperature.ThermometerDial;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Reads pure §1 ambient temperature - the environment, not the holder's
 * body. Immersion/wetness/fur/potions/amulet all belong to §7's separate
 * internalTemp system instead (see PlayerInternalTemperature), which has
 * its own dedicated display (the wordless HUD gradient bar from §7's
 * design, not this item).
 */
public class ThermometerReading implements RangeSelectItemModelProperty {

    public static final MapCodec<ThermometerReading> MAP_CODEC =
            MapCodec.unit(ThermometerReading::new);

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        if (level == null || owner == null) {
            return 0.5F;
        }
        long gameTime = level.getGameTime();
        long seasonalOffset = ClientSeasonState.getSeasonalOffset();
        var season = SeasonManager.getCurrentTemperatureSeason(gameTime, seasonalOffset);
        boolean isDay = (level.getDayTime() % 24000L) < 12000L;

        Vec3 posVec = owner.position();
        BlockPos pos = BlockPos.containing(posVec.x(), posVec.y(), posVec.z());

        double celsius = LocalTemperatureService.getAmbientTemperature(level, pos, season, isDay);
        return ThermometerDial.getFraction(celsius);
    }

    @Override
    public MapCodec<? extends RangeSelectItemModelProperty> type() {
        return MAP_CODEC;
    }
}