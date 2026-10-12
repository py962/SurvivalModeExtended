package com.terrainlab.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows nether wart to be planted on soul soil as well as soul sand. Vanilla's
 * {@code NetherWartBlock#mayPlaceOn} hardcodes soul sand rather than using a tag.
 */
@Mixin(NetherWartBlock.class)
public abstract class NetherWartSoulSoilMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[TerrainLab/NetherWartSoulSoil]";

    @Inject(
            method = "mayPlaceOn(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("RETURN"),
            cancellable = true,
            require = 1
    )
    private void terrainlab$allowSoulSoil(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && state.is(Blocks.SOUL_SOIL)) {
            // LOGGER.trace("{} allowing nether wart placement on soul_soil at {}", LOG_PREFIX, pos);

            cir.setReturnValue(true);
        }
    }
}
