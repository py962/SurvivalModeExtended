package com.terrainlab.mixin;

import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows replacing a falling block entity's block state. */
@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityAccessor {
    @Accessor("blockState")
    void terrainlab$setBlockState(BlockState state);
}
