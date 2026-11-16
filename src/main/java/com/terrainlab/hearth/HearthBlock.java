package com.terrainlab.hearth;

import net.minecraft.world.level.block.CampfireBlock;

/** Brick-ringed campfire; the evening gathering point for tier-3+ villages. */
public class HearthBlock extends CampfireBlock {

    public HearthBlock(Properties properties) {
        super(true, 1, properties);
    }
}
