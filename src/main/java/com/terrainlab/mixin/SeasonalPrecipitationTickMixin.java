package com.terrainlab.mixin;

import com.terrainlab.data.SeasonSavedData;
import com.terrainlab.season.Season;
import com.terrainlab.season.SeasonManager;
import com.terrainlab.block.SandLayerBlock;
import com.terrainlab.registry.ModBlocks;
import com.terrainlab.weather.Sandstorm;
import com.terrainlab.weather.SeasonalPrecipitation;
import com.terrainlab.weather.WeatherConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side seasonal precipitation. Replaces {@code ServerLevel#tickPrecipitation}, keeping vanilla's
 * ice, snow-layer and handlePrecipitation mechanics but taking the freeze, snow and precipitation decisions
 * from {@link SeasonalPrecipitation}; each falls back to the vanilla Biome method when the biome has no
 * seasonal data. While it rains, sandstorm biomes accumulate sand layers instead.
 */
@Mixin(ServerLevel.class)
public abstract class SeasonalPrecipitationTickMixin {

    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true, require = 1)
    private void terrainlab$seasonalTickPrecipitation(BlockPos posIn, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;

        BlockPos blockpos = self.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, posIn);
        BlockPos blockpos1 = blockpos.below();
        Biome biome = self.getBiome(blockpos).value();

        SeasonSavedData seasonData = SeasonSavedData.get(self);
        seasonData.generateOffsetIfAbsent(self.getSeed());
        Season season = SeasonManager.getCurrentSeason(self.getGameTime(), seasonData.getSeasonalOffset());
        // Solar day (sun up), which varies with season.
        boolean isDay = com.terrainlab.season.SolarTime.isSolarDay(self);

        Boolean freeze = SeasonalPrecipitation.shouldFreeze(self, blockpos1, season, isDay);
        boolean shouldFreeze = freeze != null ? freeze : biome.shouldFreeze(self, blockpos1);
        if (shouldFreeze) {
            self.setBlockAndUpdate(blockpos1, Blocks.ICE.defaultBlockState());
        }

        if (self.isRaining()) {
            if (Sandstorm.isSandstormBiome(self, blockpos)) {
                terrainlab$addSandLayer(self, blockpos);
            }

            int maxHeight = self.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);

            Boolean snow = SeasonalPrecipitation.shouldSnow(self, blockpos, season, isDay, true);
            boolean shouldSnow = snow != null ? snow : biome.shouldSnow(self, blockpos);
            if (maxHeight > 0 && shouldSnow) {
                BlockState blockstate = self.getBlockState(blockpos);
                if (blockstate.is(Blocks.SNOW)) {
                    int layers = blockstate.getValue(SnowLayerBlock.LAYERS);
                    if (layers < Math.min(maxHeight, 8)) {
                        BlockState blockstate1 = blockstate.setValue(SnowLayerBlock.LAYERS, layers + 1);
                        Block.pushEntitiesUp(blockstate, blockstate1, self, blockpos);
                        self.setBlockAndUpdate(blockpos, blockstate1);
                    }
                } else {
                    self.setBlockAndUpdate(blockpos, Blocks.SNOW.defaultBlockState());
                }
            }

            Biome.Precipitation precipitation = SeasonalPrecipitation.decide(self, blockpos1, season, isDay, true);
            if (precipitation == null) {
                precipitation = biome.getPrecipitationAt(blockpos1, self.getSeaLevel());
            }
            if (precipitation != Biome.Precipitation.NONE) {
                BlockState blockstate2 = self.getBlockState(blockpos1);
                blockstate2.getBlock().handlePrecipitation(blockstate2, self, blockpos1, precipitation);
            }
        }

        ci.cancel();
    }

    /**
     * Adds one sand layer at this surface position, up to the storm's limit. The motion-blocking heightmap
     * sees through sand layers, as with snow.
     */
    @Unique
    private static void terrainlab$addSandLayer(ServerLevel level, BlockPos pos) {
        if (!level.canSeeSky(pos)) {
            return;
        }

        int max = level.isThundering()
                ? WeatherConstants.SAND_LAYERS_SEVERE_MAX
                : WeatherConstants.SAND_LAYERS_NORMAL_MAX;
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof SandLayerBlock) {
            int layers = state.getValue(SandLayerBlock.LAYERS);
            if (layers < max) {
                BlockState grown = state.setValue(SandLayerBlock.LAYERS, layers + 1);
                Block.pushEntitiesUp(state, grown, level, pos);
                level.setBlockAndUpdate(pos, grown);
            }
            return;
        }

        // Air or a replaceable plant (dead bush, dry grass); never water or snow.
        if
 (!state.isAir()
                && !(state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(Blocks.SNOW))) {
            return;
        }

        Block layer = Sandstorm.isRedSand(level, pos) ? ModBlocks.RED_SAND_LAYER.get() : ModBlocks.SAND_LAYER.get();
        BlockState layerState = layer.defaultBlockState();
        if (layerState.canSurvive(level, pos)) {
            level.setBlockAndUpdate(pos, layerState);
        }
    }
}
