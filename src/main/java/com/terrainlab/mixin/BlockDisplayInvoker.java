package com.terrainlab.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code Display.BlockDisplay#setBlockState}, used by EyeOreSense to spawn glowing
 * ore-outline markers.
 */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayInvoker {

    @Invoker("setBlockState")
    void terrainlab$setBlockState(BlockState state);
}
