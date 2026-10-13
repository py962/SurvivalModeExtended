package com.terrainlab.mixin;

import com.terrainlab.client.ClientSeasonState;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.weather.SeasonalPrecipitation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client side of seasonal precipitation: overrides {@code ClientLevel#getPrecipitationAt} (26.x moved it
 * there from WeatherEffectRenderer), which both the rain/snow column renderer and rain particle/sound
 * ticking call.
 * <p>
 * {@code globallyRaining} is passed as true because both callers already return early when the rain
 * level is zero. Biomes without seasonal data fall through to vanilla. The chunk-loaded guard
 * mirrors vanilla's.
 */
@Mixin(ClientLevel.class)
public abstract class SeasonalPrecipitationRenderMixin {

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$seasonalPrecipitation(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        Level level = (Level) (Object) this;
        if (!level.getChunkSource().hasChunk(
                SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }

        long seasonalOffset = ClientSeasonState.getSeasonalOffset();
        Season season = SeasonManager.getCurrentSeason(level.getGameTime(), seasonalOffset);
        // Seasonal day length: 'day' means the sun is up (solar time), not a fixed dayTime half.
        boolean isDay = com.terrainlab.season.SolarTime.isSolarDay(level);

        Biome.Precipitation result = SeasonalPrecipitation.decide(level, pos, season, isDay, true);
        if (result != null) {
            cir.setReturnValue(result);
        }
        // null: biome has no seasonal data, so vanilla decides.
    }
}
