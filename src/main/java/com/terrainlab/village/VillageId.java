package com.terrainlab.village;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A village's stable identity: its dimension and the position of its anchoring bell.
 */
public record VillageId(ResourceKey<Level> dimension, BlockPos bellPos) {
}
