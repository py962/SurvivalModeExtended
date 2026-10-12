package com.terrainlab.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invoker for MapItem's private fluid-surface lookup used when colouring map pixels. */
@Mixin(MapItem.class)
public interface MapItemFluidStateAccessor {
    @Invoker("getCorrectStateForFluidBlock")
    BlockState terrainlab$getCorrectStateForFluidBlock(Level level, BlockState state, BlockPos pos);
}
